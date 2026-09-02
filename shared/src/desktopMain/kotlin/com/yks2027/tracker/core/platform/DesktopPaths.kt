package com.yks2027.tracker.core.platform

import java.io.File

/**
 * Per-OS user data dir (spec §4): ~/Library/Application Support/YKS Takip on macOS,
 * %APPDATA%\YKS Takip on Windows, $XDG_DATA_HOME/YKS Takip (or ~/.local/share) on Linux.
 * Holds yks.db, datastore/<name>.preferences_pb, the secret store and window prefs.
 */
class DesktopPaths(val dataDir: File) {

    val dataStoreDir: File get() = File(dataDir, "datastore").also { it.mkdirs() }
    val databaseFile: File get() = File(dataDir, "yks.db")
    val secretsDir: File get() = File(dataDir, "secrets").also { it.mkdirs() }
    val windowPrefsFile: File get() = File(dataDir, "window.properties")

    companion object {
        const val APP_DIR_NAME = "YKS Takip"

        fun default(): DesktopPaths = DesktopPaths(
            resolveDataDir(
                osName = System.getProperty("os.name"),
                home = System.getProperty("user.home"),
                appData = System.getenv("APPDATA"),
                xdgDataHome = System.getenv("XDG_DATA_HOME"),
            ).also { it.mkdirs() },
        )

        /** Pure, unit-tested: the per-OS user data directory for this app. */
        fun resolveDataDir(osName: String, home: String, appData: String?, xdgDataHome: String?): File {
            val os = osName.lowercase()
            val base = when {
                os.contains("mac") -> File(home, "Library/Application Support")
                os.contains("win") -> File(appData?.takeIf { it.isNotBlank() } ?: File(home, "AppData/Roaming").path)
                else -> File(xdgDataHome?.takeIf { it.isNotBlank() } ?: File(home, ".local/share").path)
            }
            return File(base, APP_DIR_NAME)
        }
    }
}
