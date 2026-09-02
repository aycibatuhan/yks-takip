package com.yks2027.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.datastore.ThemeMode
import com.yks2027.tracker.core.ui.YksTheme
import com.yks2027.tracker.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = hiltViewModel()
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

@HiltViewModel
class MainViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    backupManager: BackupManager,
    aiProfilesRepository: com.yks2027.tracker.core.ai.AiProfilesRepository,
) : ViewModel() {
    val themeMode = settingsRepository.settings
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // v1.2 — one-time single-slot → ai_profiles migration (idempotent, flagged).
            runCatching { aiProfilesRepository.migrateLegacyIfNeeded() }
            // PRD §9.4 — weekly auto-backup check on app open; never blocks or breaks start.
            runCatching { backupManager.autoBackupIfDue() }
        }
    }
}
