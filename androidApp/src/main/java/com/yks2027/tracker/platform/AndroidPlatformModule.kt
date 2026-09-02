package com.yks2027.tracker.platform

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.yks2027.tracker.core.ai.AndroidKeystoreSecretStore
import com.yks2027.tracker.core.database.DatabaseFactory
import com.yks2027.tracker.core.platform.AndroidDatabaseFactory
import com.yks2027.tracker.core.platform.AndroidImageDownscaler
import com.yks2027.tracker.core.platform.AndroidPreferencesStores
import com.yks2027.tracker.core.platform.DirEntry
import com.yks2027.tracker.core.platform.ImageDownscaler
import com.yks2027.tracker.core.platform.PickedFile
import com.yks2027.tracker.core.platform.PlatformFiles
import com.yks2027.tracker.core.platform.PreferencesStores
import com.yks2027.tracker.core.platform.SecretStore
import com.yks2027.tracker.core.platform.ShareService
import com.yks2027.tracker.core.platform.TimerCompletionScheduler
import com.yks2027.tracker.feature.timer.AlarmScheduler
import com.yks2027.tracker.feature.timer.TimerService
import com.yks2027.tracker.feature.timer.postCompletionNotification
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The one Android platform module (spec §2): binds every core.platform contract. */
val androidPlatformModule = module {
    single { ActivityBridge() }
    single<PreferencesStores> { AndroidPreferencesStores(androidContext()) }
    single<DatabaseFactory> { AndroidDatabaseFactory(androidContext()) }
    single<SecretStore> { AndroidKeystoreSecretStore(get<PreferencesStores>().open("ai_secrets")) }
    single<ImageDownscaler> { AndroidImageDownscaler() }
    single<PlatformFiles> { AndroidPlatformFiles(androidContext(), get()) }
    single<ShareService> { AndroidShareService(androidContext()) }
    single { AlarmScheduler(androidContext()) }
    single<TimerCompletionScheduler> { AndroidTimerCompletionScheduler(androidContext(), get(), get()) }
}

/** Storage Access Framework, exactly the v1.x flows (persistable tree permission for auto-backup). */
class AndroidPlatformFiles(private val context: Context, private val bridge: ActivityBridge) : PlatformFiles {

    override suspend fun pickFile(extensions: List<String>, mimeTypes: List<String>): PickedFile? {
        val uri = bridge.openDocument(mimeTypes.toTypedArray()) ?: return null
        return withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Dosya okunamadı")
            PickedFile(displayName(uri) ?: "dosya", bytes)
        }
    }

    override suspend fun saveFile(suggestedName: String, extension: String, mimeType: String, bytes: ByteArray): String? {
        val name = if (suggestedName.endsWith(".$extension")) suggestedName else "$suggestedName.$extension"
        val uri = bridge.createDocument(name, mimeType) ?: return null
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: error("Dosya yazılamadı")
        }
        return displayName(uri) ?: name
    }

    override suspend fun pickDirectory(): String? {
        val uri = bridge.openDocumentTree() ?: return null
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        return uri.toString()
    }

    override fun describeLocation(location: String): String =
        DocumentFile.fromTreeUri(context, Uri.parse(location))?.name ?: location

    override suspend fun writeToDirectory(location: String, fileName: String, mimeType: String, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(location)) ?: return@withContext false
            if (!tree.canWrite()) return@withContext false
            tree.findFile(fileName)?.delete() // same-day rerun replaces
            val file = tree.createFile(mimeType, fileName) ?: return@withContext false
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(bytes) } != null
        }

    override suspend fun listDirectory(location: String): List<DirEntry> = withContext(Dispatchers.IO) {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(location)) ?: return@withContext emptyList()
        tree.listFiles().mapNotNull { f -> f.name?.let { DirEntry(it, f.uri.toString()) } }
    }

    override suspend fun deleteFromDirectory(location: String, entry: DirEntry) {
        withContext(Dispatchers.IO) {
            DocumentFile.fromSingleUri(context, Uri.parse(entry.id))?.delete()
        }
    }

    override suspend fun writePrivateFile(name: String, text: String) {
        withContext(Dispatchers.IO) { File(context.filesDir, name).writeText(text) }
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}

/** System share sheet (text) and FileProvider-backed stream share (CSV) — v1.2 behaviour. */
class AndroidShareService(private val context: Context) : ShareService {

    override suspend fun shareText(text: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override suspend fun shareFile(fileName: String, mimeType: String, bytes: ByteArray, title: String) {
        val uri = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, fileName).apply { writeBytes(bytes) }
            FileProvider.getUriForFile(context, "com.yks2027.tracker.fileprovider", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * PRD §7.2 on Android, untouched from v1.x: exact alarm (completion guarantee) + foreground
 * service (live chronometer notification) + completion notification. The boot receiver
 * reschedules after reboot on its own.
 */
class AndroidTimerCompletionScheduler(
    private val context: Context,
    private val alarmScheduler: AlarmScheduler,
    private val bridge: ActivityBridge,
) : TimerCompletionScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun scheduleCompletion(endAtEpochMs: Long) = alarmScheduler.schedule(endAtEpochMs)
    override fun cancelCompletion() = alarmScheduler.cancel()
    override fun showCountdown(endAtEpochMs: Long) = TimerService.startCountdown(context, endAtEpochMs)
    override fun showStopwatch(elapsedMs: Long) = TimerService.startStopwatch(context, elapsedMs)
    override fun hideRunning() = TimerService.stop(context)
    override fun notifyCompleted(title: String, text: String) = postCompletionNotification(context, title, text)

    /** Contextual POST_NOTIFICATIONS ask on first start (Android 13+); a denial still starts. */
    override fun ensureNotificationPermission(onDone: () -> Unit) {
        val needs = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (!needs) {
            onDone()
            return
        }
        scope.launch {
            bridge.requestPermission(Manifest.permission.POST_NOTIFICATIONS)
            onDone()
        }
    }
}
