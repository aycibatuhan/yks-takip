package com.yks2027.tracker.core.platform

import java.io.File

/**
 * Per-OS user data dir (spec §4): ~/Library/Application Support/YKS Takip on macOS,
 * %APPDATA%\YKS Takip on Windows, $XDG_DATA_HOME/YKS Takip (or ~/.local/share) on Linux.
 * Holds yks.db, datastore/*.preferences_pb, the secret store and window prefs.
 */
class DesktopPaths(val dataDir: File) {

    val dataStoreDir: File get() = File(dataDir, "datastore").also { it.mkdirs() }
    val databaseFile: File get() = File(dataDir, "yks.db")
    val secretsDir: File get() = File(dataDir, "secrets").also { it.mkdirs() }
    val windowPrefsFile: File get() = File(dataDir, "window.properties")

    companion object {
        const val APP_DIR_NAME = "YKS Takip"

        fun default(): DesktopPaths {
            val os = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")
            val base = when {
                os.contains("mac") -> File(home, "Library/Application Support")
                os.contains("win") -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path)
                else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path)
            }
            return DesktopPaths(File(base, APP_DIR_NAME).also { it.mkdirs() })
        }
    }
}
