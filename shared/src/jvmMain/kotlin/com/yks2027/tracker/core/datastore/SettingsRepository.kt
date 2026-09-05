package com.yks2027.tracker.core.datastore

import com.yks2027.tracker.core.platform.SecretStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yks2027.tracker.core.time.ISTANBUL
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val tytExamAt: Long,
    val aytExamAt: Long,
    val datesConfirmed: Boolean,
    val themeMode: ThemeMode,
    val backupDirUri: String?,
    val lastBackupAt: Long?,
    // Module E — AI Koç. v1.2: config lives in ai_profiles (Room); settings only
    // remember WHICH profile is active. Keys are in SecretStore, per profile.
    val activeAiProfileId: Long?,
    val aiShareStats: Boolean,
    /** v2.1 — let the coach use the provider's server-side web search (Anthropic profiles only). */
    val aiWebSearch: Boolean,
    // Timer auto-break suggestion length in minutes; 0 = off (PRD §12 M3).
    val autoBreakMin: Int,
) {
    /** v1.2 rebrand: display year derived from the configured TYT date ("YKS 2027"). */
    val yksYearLabel: String
        get() = "YKS " + Instant.ofEpochMilli(tytExamAt).atZone(ISTANBUL).year
}

/** Raw v1.0/v1.1 single-slot AI prefs, read once by the profile migration then cleared. */
data class LegacyAiSlot(
    val providerName: String?,
    val baseUrl: String?,
    val model: String?,
)

/** PRD §9.3 — the `settings` preferences file (separate from `timer_state`), opened by the platform. */
class SettingsRepository(private val settingsStore: DataStore<Preferences>) {

    private object Keys {
        val TYT_EXAM_AT = longPreferencesKey("tyt_exam_at")
        val AYT_EXAM_AT = longPreferencesKey("ayt_exam_at")
        val DATES_CONFIRMED = booleanPreferencesKey("exam_dates_confirmed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BACKUP_DIR_URI = stringPreferencesKey("backup_dir_uri")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val AI_SHARE_STATS = booleanPreferencesKey("ai_share_stats")
        val AI_WEB_SEARCH = booleanPreferencesKey("ai_web_search")
        val AUTO_BREAK_MIN = intPreferencesKey("auto_break_min")
        // v1.2 profiles
        val ACTIVE_AI_PROFILE_ID = longPreferencesKey("active_ai_profile_id")
        val AI_PROFILES_MIGRATED = booleanPreferencesKey("ai_profiles_migrated")
        // Legacy v1.0/v1.1 single-slot keys — read by the migration, then removed.
        val LEGACY_AI_PROVIDER = stringPreferencesKey("ai_provider")
        val LEGACY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val LEGACY_AI_MODEL = stringPreferencesKey("ai_model")
    }

    val settings: Flow<Settings> = settingsStore.data.map { p ->
        Settings(
            tytExamAt = p[Keys.TYT_EXAM_AT] ?: DEFAULT_TYT_EXAM_AT,
            aytExamAt = p[Keys.AYT_EXAM_AT] ?: DEFAULT_AYT_EXAM_AT,
            datesConfirmed = p[Keys.DATES_CONFIRMED] ?: false,
            themeMode = p[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            backupDirUri = p[Keys.BACKUP_DIR_URI],
            lastBackupAt = p[Keys.LAST_BACKUP_AT],
            activeAiProfileId = p[Keys.ACTIVE_AI_PROFILE_ID],
            aiShareStats = p[Keys.AI_SHARE_STATS] ?: true,
            aiWebSearch = p[Keys.AI_WEB_SEARCH] ?: false,
            autoBreakMin = p[Keys.AUTO_BREAK_MIN] ?: 0,
        )
    }

    suspend fun setTytExamAt(epochMs: Long) = settingsStore.edit { it[Keys.TYT_EXAM_AT] = epochMs }
    suspend fun setAytExamAt(epochMs: Long) = settingsStore.edit { it[Keys.AYT_EXAM_AT] = epochMs }
    suspend fun setDatesConfirmed(v: Boolean) = settingsStore.edit { it[Keys.DATES_CONFIRMED] = v }
    suspend fun setThemeMode(v: ThemeMode) = settingsStore.edit { it[Keys.THEME_MODE] = v.name }
    suspend fun setLastBackupAt(epochMs: Long) = settingsStore.edit { it[Keys.LAST_BACKUP_AT] = epochMs }

    suspend fun setBackupDirUri(uri: String?) = settingsStore.edit { prefs ->
        if (uri == null) prefs.remove(Keys.BACKUP_DIR_URI) else prefs[Keys.BACKUP_DIR_URI] = uri
    }

    suspend fun setAiShareStats(v: Boolean) = settingsStore.edit { it[Keys.AI_SHARE_STATS] = v }
    suspend fun setAiWebSearch(v: Boolean) = settingsStore.edit { it[Keys.AI_WEB_SEARCH] = v }
    suspend fun setAutoBreakMin(v: Int) = settingsStore.edit { it[Keys.AUTO_BREAK_MIN] = v.coerceIn(0, 60) }

    suspend fun setActiveAiProfileId(id: Long?) = settingsStore.edit { prefs ->
        if (id == null) prefs.remove(Keys.ACTIVE_AI_PROFILE_ID) else prefs[Keys.ACTIVE_AI_PROFILE_ID] = id
    }

    // --- v1.2 single-slot → profile migration support ---

    suspend fun aiProfilesMigrated(): Boolean =
        settingsStore.data.first()[Keys.AI_PROFILES_MIGRATED] ?: false

    /** Legacy prefs as stored; providerName == null means the user never picked a provider. */
    suspend fun legacyAiSlotOnce(): LegacyAiSlot {
        val p = settingsStore.data.first()
        return LegacyAiSlot(
            providerName = p[Keys.LEGACY_AI_PROVIDER],
            baseUrl = p[Keys.LEGACY_AI_BASE_URL],
            model = p[Keys.LEGACY_AI_MODEL],
        )
    }

    suspend fun markAiProfilesMigrated() = settingsStore.edit { prefs ->
        prefs[Keys.AI_PROFILES_MIGRATED] = true
        prefs.remove(Keys.LEGACY_AI_PROVIDER)
        prefs.remove(Keys.LEGACY_AI_BASE_URL)
        prefs.remove(Keys.LEGACY_AI_MODEL)
    }

    companion object {
        /**
         * PRD §3.3 — ÖSYM has not announced the 2027 calendar. Defaults follow the
         * recent pattern (TYT Saturday, AYT Sunday, 10:15 starts); the UI shows a
         * "tahmini tarih" banner until datesConfirmed is set.
         */
        val DEFAULT_TYT_EXAM_AT: Long =
            LocalDateTime.of(2027, 6, 19, 10, 15).atZone(ISTANBUL).toInstant().toEpochMilli()
        val DEFAULT_AYT_EXAM_AT: Long =
            LocalDateTime.of(2027, 6, 20, 10, 15).atZone(ISTANBUL).toInstant().toEpochMilli()
    }
}
