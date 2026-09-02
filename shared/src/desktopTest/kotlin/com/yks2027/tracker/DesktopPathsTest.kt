package com.yks2027.tracker

import com.yks2027.tracker.core.platform.DesktopPaths
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** v2.0 — cross-platform data-dir resolution (spec §4): the backup/db/datastore root per OS. */
class DesktopPathsTest {

    @Test
    fun macOsUsesApplicationSupport() {
        val dir = DesktopPaths.resolveDataDir("Mac OS X", "/Users/ali", appData = null, xdgDataHome = null)
        assertEquals(File("/Users/ali/Library/Application Support/YKS Takip"), dir)
    }

    @Test
    fun windowsUsesAppDataWithFallback() {
        assertEquals(
            File("C:\\Users\\ali\\AppData\\Roaming", "YKS Takip"),
            DesktopPaths.resolveDataDir("Windows 11", "C:\\Users\\ali", appData = "C:\\Users\\ali\\AppData\\Roaming", xdgDataHome = null),
        )
        assertEquals(
            File("C:\\Users\\ali", "AppData/Roaming").resolve("YKS Takip"),
            DesktopPaths.resolveDataDir("Windows 11", "C:\\Users\\ali", appData = "", xdgDataHome = null),
        )
    }

    @Test
    fun linuxHonoursXdgDataHome() {
        assertEquals(File("/data/x/YKS Takip"), DesktopPaths.resolveDataDir("Linux", "/home/ali", null, "/data/x"))
        assertEquals(File("/home/ali/.local/share/YKS Takip"), DesktopPaths.resolveDataDir("Linux", "/home/ali", null, null))
    }

    @Test
    fun derivedLocationsLiveUnderTheDataDir() {
        val root = File("/tmp/yks-root")
        val p = DesktopPaths(root)
        assertEquals(File(root, "yks.db"), p.databaseFile)
        assertEquals(File(root, "window.properties"), p.windowPrefsFile)
    }
}
