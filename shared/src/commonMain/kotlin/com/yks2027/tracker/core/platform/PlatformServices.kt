package com.yks2027.tracker.core.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

/*
 * v2.0 — THE platform boundary (see docs/ARCHITECTURE.md §Platform boundary). Shared code
 * talks only to these contracts; each platform binds its implementation in its own Koin
 * module (androidPlatformModule / desktopPlatformModule). Nothing else in shared code may
 * import android.* or java.awt.*.
 */

/**
 * API keys at rest. Android: Keystore AES-GCM in the `ai_secrets` DataStore (ciphertext
 * format unchanged from v1.x, so existing keys survive the v2 update). Desktop: AES-GCM
 * file with a per-installation random key in the user config dir (owner-only permissions)
 * — weaker than a hardware keystore; documented in the README.
 */
interface SecretStore {
    /** ids of profiles that currently have a stored key (values stay encrypted). */
    val profileIdsWithKey: Flow<Set<Long>>
    suspend fun getKey(profileId: Long): String?
    suspend fun setKey(profileId: Long, apiKey: String)
    suspend fun clearKey(profileId: Long)
    /** v1.0/v1.1 single-slot key present? (Android only ever has one; desktop: false.) */
    suspend fun hasLegacyKey(): Boolean
    /** Re-keys the legacy ciphertext onto a profile entry without decrypting to UI. */
    suspend fun migrateLegacyKeyTo(profileId: Long)
}

/**
 * Timer completion + "running" indication. Android: exact alarm + foreground service +
 * boot receiver (untouched from v1.x). Desktop: coroutine tick + system notification.
 * The TimerMode state machine and its DataStore persistence stay shared and identical.
 */
interface TimerCompletionScheduler {
    /** Guarantee a completion signal at [endAtEpochMs] even if the UI is gone. */
    fun scheduleCompletion(endAtEpochMs: Long)
    fun cancelCompletion()
    fun showCountdown(endAtEpochMs: Long)
    fun showStopwatch(elapsedMs: Long)
    fun hideRunning()
    fun notifyCompleted(title: String, text: String)
    /** POST_NOTIFICATIONS on Android 13+ (asks once); no-op elsewhere. Always calls [onDone]. */
    fun ensureNotificationPermission(onDone: () -> Unit)
}

class PickedFile(val name: String, val bytes: ByteArray)

/** A child of a picked directory; [id] is platform-opaque (document uri / absolute path). */
data class DirEntry(val name: String, val id: String)

/**
 * File dialogs + directory access. Android: Storage Access Framework (persistable tree
 * permission for the auto-backup folder). Desktop: native dialogs via FileKit.
 * Locations are opaque strings persisted in settings (`backup_dir_uri`).
 */
interface PlatformFiles {
    suspend fun pickFile(extensions: List<String>, mimeTypes: List<String>): PickedFile?
    /** Save dialog; returns a human-readable destination (or null when cancelled). */
    suspend fun saveFile(suggestedName: String, extension: String, mimeType: String, bytes: ByteArray): String?
    /** Directory dialog; returns an opaque location handle or null when cancelled. */
    suspend fun pickDirectory(): String?
    fun describeLocation(location: String): String
    suspend fun writeToDirectory(location: String, fileName: String, mimeType: String, bytes: ByteArray): Boolean
    suspend fun listDirectory(location: String): List<DirEntry>
    suspend fun deleteFromDirectory(location: String, entry: DirEntry)
    /** App-private scratch file (e.g. the pre-import safety snapshot). */
    suspend fun writePrivateFile(name: String, text: String)
}

/** Android: system share sheet. Desktop: "Dosyayı kaydet" + "Panoya kopyala". Returns a snackbar hint or null. */
interface ShareService {
    suspend fun shareText(text: String, title: String): String?
    suspend fun shareFile(fileName: String, mimeType: String, bytes: ByteArray, title: String): String?
}

/** Longest side ≤ [maxPx], JPEG [quality]. Android: BitmapFactory; desktop: javax.imageio. */
interface ImageDownscaler {
    fun toJpeg(bytes: ByteArray, maxPx: Int = 1568, quality: Int = 85): ByteArray
}

/**
 * Preferences DataStore files. Android: `<filesDir>/datastore/<name>.preferences_pb` — the
 * exact path `preferencesDataStore(name)` used in v1.x, so settings/timer/keys carry over.
 * Desktop: `<user data dir>/datastore/<name>.preferences_pb`.
 */
interface PreferencesStores {
    fun open(name: String): DataStore<Preferences>
}
