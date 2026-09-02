package com.yks2027.tracker

import com.yks2027.tracker.core.di.sharedModules
import com.yks2027.tracker.core.platform.DesktopPaths
import com.yks2027.tracker.core.platform.desktopPlatformModule
import java.io.File
import java.nio.file.Files
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Boots the real shared + desktop Koin graph against a temp data dir. When
 * `YKS_SEED_DB` points at a v1.3 Android `yks.db`, it is copied in first — the
 * cross-platform "real database opens unchanged" proof. Output PNGs go to `YKS_TOUR_OUT`.
 */
object DesktopTestApp {
    val dataDir: File by lazy { Files.createTempDirectory("yks-desktop-test").toFile() }
    val tourOut: File by lazy { File(System.getenv("YKS_TOUR_OUT") ?: "build/tour").also { it.mkdirs() } }

    @Synchronized
    fun ensureStarted(): DesktopPaths {
        val paths = DesktopPaths(dataDir)
        if (GlobalContext.getOrNull() == null) {
            System.getenv("YKS_SEED_DB")?.let { seed -> File(seed).takeIf { it.exists() }?.copyTo(paths.databaseFile, overwrite = true) }
            startKoin {
                modules(sharedModules + desktopPlatformModule + module { single { paths } })
            }
        }
        return paths
    }
}
