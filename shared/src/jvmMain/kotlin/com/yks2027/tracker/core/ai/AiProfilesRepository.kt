package com.yks2027.tracker.core.ai

import com.yks2027.tracker.core.platform.SecretStore
import com.yks2027.tracker.core.database.AiProfileDao
import com.yks2027.tracker.core.database.AiProfileEntity
import com.yks2027.tracker.core.datastore.LegacyAiSlot
import com.yks2027.tracker.core.datastore.SettingsRepository
import com.yks2027.tracker.core.time.IstanbulClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pure decision logic for the one-time v1.0/v1.1 single-slot → ai_profiles migration.
 * Unit-tested; the repository below only executes the returned spec.
 */
object LegacyAiMigration {

    data class Spec(val name: String, val protocol: AiProtocol, val baseUrl: String, val model: String)

    /**
     * A profile is created when the old slot was actually in use: a stored key exists
     * (any provider), or the user explicitly selected a keyless provider (Ollama).
     * A fresh install — provider pref never written, no key — migrates to nothing.
     */
    fun specFor(slot: LegacyAiSlot, hadKey: Boolean): Spec? {
        val provider = slot.providerName
            ?.let { name -> runCatching { AiProviderKind.valueOf(name) }.getOrNull() }
        val effective = provider ?: AiProviderKind.ANTHROPIC
        val configured = hadKey || (provider != null && !provider.requiresKey)
        if (!configured) return null
        return Spec(
            name = effective.label,
            protocol = effective.protocol,
            baseUrl = slot.baseUrl?.takeIf { it.isNotBlank() }
                ?: effective.defaultBaseUrl
                ?: ANTHROPIC_DEFAULT_BASE_URL,
            model = slot.model?.takeIf { it.isNotBlank() } ?: effective.defaultModel,
        )
    }

    const val ANTHROPIC_DEFAULT_BASE_URL = "https://api.anthropic.com"
}

class AiProfilesRepository constructor(
    private val dao: AiProfileDao,
    private val settingsRepository: SettingsRepository,
    private val secrets: SecretStore,
    private val clock: IstanbulClock,
) {

    private val migrationLock = Mutex()

    val profiles: Flow<List<AiProfileEntity>> = dao.observeAll()

    val activeProfile: Flow<AiProfileEntity?> =
        combine(dao.observeAll(), settingsRepository.settings) { all, settings ->
            all.firstOrNull { it.id == settings.activeAiProfileId } ?: all.firstOrNull()
        }

    fun protocolOf(profile: AiProfileEntity): AiProtocol =
        runCatching { AiProtocol.valueOf(profile.protocol) }.getOrDefault(AiProtocol.OPENAI_COMPAT)

    suspend fun activeProfileOnce(): AiProfileEntity? = activeProfile.first()

    /** Saves (id=0 creates) and returns the row id; a first profile becomes active. */
    suspend fun save(profile: AiProfileEntity): Long {
        val toSave = if (profile.id == 0L) {
            profile.copy(createdAt = clock.now().toEpochMilli())
        } else {
            // Edits keep the original creation stamp (it drives list ordering).
            profile.copy(createdAt = dao.byId(profile.id)?.createdAt ?: clock.now().toEpochMilli())
        }
        val id = dao.upsert(toSave)
        val savedId = if (profile.id != 0L) profile.id else id
        if (settingsRepository.settings.first().activeAiProfileId == null) {
            settingsRepository.setActiveAiProfileId(savedId)
        }
        return savedId
    }

    suspend fun setActive(id: Long) = settingsRepository.setActiveAiProfileId(id)

    /** Deletes the profile AND its stored key; re-points active to any remaining profile. */
    suspend fun delete(id: Long) {
        dao.deleteById(id)
        secrets.clearKey(id)
        val settings = settingsRepository.settings.first()
        if (settings.activeAiProfileId == id) {
            settingsRepository.setActiveAiProfileId(dao.allOnce().firstOrNull()?.id)
        }
    }

    /**
     * One-time v1.0/v1.1 → v1.2 migration: turns the single-slot config into an active
     * profile and re-keys the stored API key onto it. Idempotent (flag + lock); called
     * from app start — must never throw into the caller.
     */
    suspend fun migrateLegacyIfNeeded() = migrationLock.withLock {
        if (settingsRepository.aiProfilesMigrated()) return@withLock
        val hadKey = secrets.hasLegacyKey()
        val spec = LegacyAiMigration.specFor(settingsRepository.legacyAiSlotOnce(), hadKey)
        if (spec != null && dao.count() == 0) {
            val id = dao.upsert(
                AiProfileEntity(
                    name = spec.name,
                    protocol = spec.protocol.name,
                    baseUrl = spec.baseUrl,
                    model = spec.model,
                    createdAt = clock.now().toEpochMilli(),
                ),
            )
            if (hadKey) secrets.migrateLegacyKeyTo(id)
            settingsRepository.setActiveAiProfileId(id)
        }
        settingsRepository.markAiProfilesMigrated()
    }
}
