package com.yks2027.tracker.core.ai

import com.yks2027.tracker.core.platform.SecretStore
import com.yks2027.tracker.core.database.AiProfileEntity
import com.yks2027.tracker.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Prompt assembly + dispatch to the active profile's client (PRD §8, v1.2 profiles).
 * The coach persona is fixed; the stats block is appended only when ai_share_stats is
 * on. "Tutor, not answer key" caution is baked into the persona.
 */
class AiClient constructor(
    private val anthropicProvider: AnthropicProvider,
    private val openAiCompatProvider: OpenAiCompatProvider,
    private val secrets: SecretStore,
    private val profilesRepository: AiProfilesRepository,
    private val settingsRepository: SettingsRepository,
    private val statsContextBuilder: StatsContextBuilder,
) {

    /**
     * AI Koç appears once at least one profile exists. A profile only exists because
     * the user created (or migrated) one, so the zero-config = zero-network guarantee
     * holds. A missing key surfaces as the server's own 401 with detail, not a dead tab.
     */
    val configured: Flow<Boolean> = profilesRepository.activeProfile.map { it != null }

    fun streamReply(history: List<AiChatMessage>): Flow<String> = flow {
        val profile = profilesRepository.activeProfileOnce()
            ?: throw AiException("AI profili yok — Ayarlar → AI Koç bölümünden ekle.")
        val apiKey = secrets.getKey(profile.id).orEmpty()
        if (profile.model.isBlank()) {
            throw AiException("Bu profilde model seçilmemiş — profil ayarlarından \"Modelleri Getir\" ile seç.")
        }
        val settings = settingsRepository.settings.first()
        val protocol = profilesRepository.protocolOf(profile)
        val webSearch = settings.aiWebSearch && protocol == AiProtocol.ANTHROPIC
        val system = buildSystemPrompt(settings.aiShareStats, webSearch)
        val upstream = when (protocol) {
            AiProtocol.ANTHROPIC ->
                anthropicProvider.streamChat(apiKey, profile.baseUrl, profile.model, system, history, webSearch = webSearch)
            AiProtocol.OPENAI_COMPAT -> {
                if (profile.baseUrl.isBlank()) throw AiException("Bu profilde taban URL ayarlanmamış.")
                openAiCompatProvider.streamChat(apiKey, profile.baseUrl, profile.model, system, history)
            }
        }
        emitAll(upstream)
    }

    /** "Modelleri Getir" / "Bağlantıyı Sına" — dispatches on the profile's protocol. */
    suspend fun listModels(profile: AiProfileEntity, keyOverride: String? = null): List<AiModelInfo> {
        val apiKey = keyOverride ?: secrets.getKey(profile.id).orEmpty()
        return when (profilesRepository.protocolOf(profile)) {
            AiProtocol.ANTHROPIC -> anthropicProvider.listModels(apiKey, profile.baseUrl)
            AiProtocol.OPENAI_COMPAT -> {
                if (profile.baseUrl.isBlank()) throw AiException("Taban URL boş — önce doldur.")
                openAiCompatProvider.listModels(apiKey, profile.baseUrl)
            }
        }
    }

    private suspend fun buildSystemPrompt(shareStats: Boolean, webSearch: Boolean): String = buildString {
        val yearLabel = settingsRepository.settings.first().yksYearLabel
        appendLine(
            "Sen $yearLabel'ye Sayısal alanından hazırlanan bir lise öğrencisinin kişisel " +
                "koçusun. Türkçe konuş. Kısa, somut ve cesaretlendirici yanıtlar ver; " +
                "listeler kullanabilirsin. Matematik/fen sorularında adım adım ilerle ve " +
                "sonucun yanlış olabileceğini unutma — emin olmadığında bunu açıkça söyle " +
                "(sen bir öğretmensin, cevap anahtarı değilsin). Çalışma planı önerirken " +
                "öğrencinin gerçek verilerine dayan; veri yoksa varsayım yapma, sor.",
        )
        if (shareStats) {
            appendLine(
                "Aşağıdaki veriler uygulamadan otomatik gelir (haftalık plan gün gün, konu takibi, " +
                    "odak süreleri, denemeler, notlar). Öğrenci \"programımı gördün mü?\" diye sorarsa " +
                    "bu verilere dayanarak somut yorum yap; uydurma, eksikse söyle.",
            )
        }
        if (webSearch) {
            appendLine(
                "Web araması açık: güncel bilgi gerektiğinde (ÖSYM takvimi, duyurular, kaynak " +
                    "önerileri) ara ve kaynağını belirt. Gereksiz yere arama yapma.",
            )
        }
        if (shareStats) {
            appendLine()
            append(statsContextBuilder.build())
        }
    }.trim()
}
