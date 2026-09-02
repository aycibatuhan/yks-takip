package com.yks2027.tracker.core.platform

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bridges shared code to the Compose Desktop tray: system notifications plus a
 * "running" label the window title mirrors. The tray state is attached by desktopApp.
 */
class TrayNotifier {
    @Volatile var trayState: TrayState? = null

    private val _runningLabel = MutableStateFlow<String?>(null)
    val runningLabel: StateFlow<String?> = _runningLabel

    fun notify(title: String, text: String) {
        trayState?.sendNotification(Notification(title, text, Notification.Type.Info))
    }

    fun setRunning(label: String?) {
        _runningLabel.value = label
    }
}

/** Native dialogs via FileKit; directory access with plain java.io (locations are absolute paths). */
class DesktopPlatformFiles(private val paths: DesktopPaths) : PlatformFiles {

    override suspend fun pickFile(extensions: List<String>, mimeTypes: List<String>): PickedFile? {
        val file = FileKit.openFilePicker(type = FileKitType.File(extensions)) ?: return null
        return PickedFile(file.name, file.readBytes())
    }

    override suspend fun saveFile(suggestedName: String, extension: String, mimeType: String, bytes: ByteArray): String? {
        val base = suggestedName.removeSuffix(".$extension")
        val file = FileKit.openFileSaver(suggestedName = base, extension = extension) ?: return null
        file.write(bytes)
        return file.path
    }

    override suspend fun pickDirectory(): String? = FileKit.openDirectoryPicker()?.path

    override fun describeLocation(location: String): String = location

    override suspend fun writeToDirectory(location: String, fileName: String, mimeType: String, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            val dir = File(location)
            if (!dir.isDirectory || !dir.canWrite()) return@withContext false
            runCatching { File(dir, fileName).writeBytes(bytes) }.isSuccess
        }

    override suspend fun listDirectory(location: String): List<DirEntry> = withContext(Dispatchers.IO) {
        File(location).listFiles()?.filter { it.isFile }?.map { DirEntry(it.name, it.absolutePath) } ?: emptyList()
    }

    override suspend fun deleteFromDirectory(location: String, entry: DirEntry) {
        withContext(Dispatchers.IO) { File(entry.id).takeIf { it.parentFile == File(location) }?.delete() }
    }

    override suspend fun writePrivateFile(name: String, text: String) {
        withContext(Dispatchers.IO) { File(paths.dataDir, name).writeText(text) }
    }
}

/**
 * Spec §3 — desktop "share" = "Panoya kopyala" + "Dosyayı kaydet": the text/CSV goes to
 * the clipboard immediately and a save dialog offers a file copy; a notification confirms.
 */
class DesktopShareService(private val files: PlatformFiles, private val notifier: TrayNotifier) : ShareService {

    override suspend fun shareText(text: String, title: String) {
        copyToClipboard(text)
        val saved = files.saveFile("yks_rapor", "txt", "text/plain", text.encodeToByteArray())
        notifier.notify(title, if (saved != null) "Panoya kopyalandı ve kaydedildi: $saved" else "Panoya kopyalandı")
    }

    override suspend fun shareFile(fileName: String, mimeType: String, bytes: ByteArray, title: String) {
        copyToClipboard(bytes.decodeToString())
        val ext = fileName.substringAfterLast('.', "")
        val saved = files.saveFile(fileName, ext, mimeType, bytes)
        notifier.notify(title, if (saved != null) "Panoya kopyalandı ve kaydedildi: $saved" else "Panoya kopyalandı")
    }

    private fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}

/**
 * Spec §3 — desktop completion: a coroutine sleeps until end_at and runs [onDue] (which
 * finalizes through the shared TimerController and posts the notification). The
 * persisted state machine is the source of truth: on relaunch, TimerViewModel's in-app
 * fallback tick finalizes an already-expired timer exactly as on Android.
 */
class DesktopTimerCompletionScheduler(
    private val notifier: TrayNotifier,
    private val onDue: suspend () -> Unit,
) : TimerCompletionScheduler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun scheduleCompletion(endAtEpochMs: Long) {
        job?.cancel()
        job = scope.launch {
            delay((endAtEpochMs - System.currentTimeMillis()).coerceAtLeast(0))
            onDue()
        }
    }

    override fun cancelCompletion() {
        job?.cancel(); job = null
    }

    override fun showCountdown(endAtEpochMs: Long) = notifier.setRunning("Sayaç çalışıyor")
    override fun showStopwatch(elapsedMs: Long) = notifier.setRunning("Kronometre çalışıyor")
    override fun hideRunning() = notifier.setRunning(null)
    override fun notifyCompleted(title: String, text: String) = notifier.notify(title, text)
    override fun ensureNotificationPermission(onDone: () -> Unit) = onDone()
}
