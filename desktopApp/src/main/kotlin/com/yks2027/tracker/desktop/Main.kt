package com.yks2027.tracker.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yks2027.tracker.core.datastore.ThemeMode
import com.yks2027.tracker.core.di.sharedModules
import com.yks2027.tracker.core.platform.DesktopPaths
import com.yks2027.tracker.core.platform.TrayNotifier
import com.yks2027.tracker.core.platform.desktopPlatformModule
import com.yks2027.tracker.core.ui.KeyboardShortcuts
import com.yks2027.tracker.core.ui.YksTheme
import com.yks2027.tracker.ui.AppRoot
import com.yks2027.tracker.ui.MainViewModel
import io.github.vinceglb.filekit.FileKit
import java.awt.Dimension
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.startKoin

@OptIn(FlowPreview::class)
fun main() {
    FileKit.init(appId = "YksTakip")
    startKoin { modules(sharedModules + desktopPlatformModule) }

    application {
        val paths: DesktopPaths = koinInject()
        val notifier: TrayNotifier = koinInject()
        val prefs = remember { WindowPrefs.load(paths.windowPrefsFile) }
        val windowState = rememberWindowState(
            size = DpSize(prefs.width.dp, prefs.height.dp),
            position = if (prefs.x != null && prefs.y != null) WindowPosition(prefs.x.dp, prefs.y.dp) else WindowPosition.PlatformDefault,
        )
        val trayState = rememberTrayState()
        notifier.trayState = trayState
        val running by notifier.runningLabel.collectAsStateWithLifecycle()

        Tray(
            state = trayState,
            icon = rememberVectorPainter(Icons.Outlined.School),
            tooltip = "YKS Takip",
            menu = { Item("Çıkış", onClick = ::exitApplication) },
        )

        // Remember size/position (spec §7): debounced writes while the user drags/resizes.
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.size to windowState.position }
                .debounce(600)
                .collect { (size, pos) -> WindowPrefs.save(paths.windowPrefsFile, size, pos) }
        }

        Window(
            onCloseRequest = {
                WindowPrefs.save(paths.windowPrefsFile, windowState.size, windowState.position)
                exitApplication()
            },
            state = windowState,
            title = if (running != null) "YKS Takip — $running" else "YKS Takip",
            onKeyEvent = { e ->
                // Cmd/Ctrl+N → new chat in AI Koç; Esc → close dialogs (shared shortcut bus).
                if (e.type != KeyEventType.KeyDown) return@Window false
                when {
                    e.key == Key.N && (e.isMetaPressed || e.isCtrlPressed) -> { KeyboardShortcuts.emit(KeyboardShortcuts.Shortcut.NewChat); true }
                    e.key == Key.Escape -> { KeyboardShortcuts.emit(KeyboardShortcuts.Shortcut.Escape); false }
                    else -> false
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(900, 600) }
            KoinContext {
                val vm: MainViewModel = koinViewModel()
                val mode by vm.themeMode.collectAsStateWithLifecycle()
                val dark = when (mode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                YksTheme(darkTheme = dark) {
                    AppRoot()
                }
            }
        }
    }
}
