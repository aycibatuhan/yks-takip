package com.yks2027.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yks2027.tracker.core.datastore.ThemeMode
import com.yks2027.tracker.core.ui.YksTheme
import com.yks2027.tracker.platform.ActivityBridge
import com.yks2027.tracker.ui.AppRoot
import com.yks2027.tracker.ui.MainViewModel
import org.koin.android.ext.android.inject
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {

    private val bridge: ActivityBridge by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SAF / permission launchers must be registered before STARTED (PlatformFiles bridge).
        bridge.attach(this)
        enableEdgeToEdge()
        setContent {
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

    override fun onDestroy() {
        bridge.detach(this)
        super.onDestroy()
    }
}
