package com.yks2027.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.ai.AiProfilesRepository
import com.yks2027.tracker.core.backup.BackupManager
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.datastore.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Root view model for both apps: theme mode + the once-per-launch housekeeping. */
class MainViewModel(
    settingsRepository: SettingsRepository,
    backupManager: BackupManager,
    aiProfilesRepository: AiProfilesRepository,
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
