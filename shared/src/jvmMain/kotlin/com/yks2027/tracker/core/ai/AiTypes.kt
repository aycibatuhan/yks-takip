package com.yks2027.tracker.core.ai

/**
 * v1.2 — wire protocol of an AI profile. Two client implementations cover everything:
 * AnthropicProvider (official Java SDK) for ANTHROPIC, OpenAiCompatProvider for
 * OPENAI_COMPAT. Persisted as TEXT in ai_profiles — don't rename constants.
 */
enum class AiProtocol(val label: String) {
    ANTHROPIC("Anthropic"),
    OPENAI_COMPAT("OpenAI uyumlu"),
}

/**
 * Legacy single-slot provider enum (v1.0–v1.1 settings). Kept ONLY so the one-time
 * migration into ai_profiles can map old preference values; new code uses AiProfile.
 */
enum class AiProviderKind(
    val label: String,
    val defaultBaseUrl: String?,
    val defaultModel: String,
    val requiresKey: Boolean,
) {
    ANTHROPIC("Claude (Anthropic)", null, "claude-opus-5", true),
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5-mini", true),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true),
    XAI("xAI (Grok)", "https://api.x.ai/v1", "grok-4", true),
    OLLAMA("Ollama (yerel ağ)", "http://192.168.1.100:11434/v1", "llama3.1", false);

    val protocol: AiProtocol
        get() = if (this == ANTHROPIC) AiProtocol.ANTHROPIC else AiProtocol.OPENAI_COMPAT
}

/**
 * v1.2 profile templates ("şablondan ekle"). Base URLs and default models verified
 * against provider docs on 2026-08-30. Every field stays editable after creation —
 * the base URL too (proxies for Anthropic, self-hosted gateways, etc.).
 * An empty model means "pick via Modelleri Getir" (Ollama lists local models;
 * OpenCode Zen's curated ids shift too often to hardcode one).
 */
data class AiTemplate(
    val name: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val model: String,
    val keyHint: String?,
) {
    companion object {
        val ALL = listOf(
            AiTemplate("Claude (Anthropic)", AiProtocol.ANTHROPIC, "https://api.anthropic.com", "claude-opus-5", "console.anthropic.com → API keys"),
            AiTemplate("OpenAI", AiProtocol.OPENAI_COMPAT, "https://api.openai.com/v1", "gpt-5-mini", "platform.openai.com → API keys"),
            AiTemplate("Google Gemini", AiProtocol.OPENAI_COMPAT, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-3.7-flash", "aistudio.google.com → API key"),
            AiTemplate("xAI (Grok)", AiProtocol.OPENAI_COMPAT, "https://api.x.ai/v1", "grok-4.6", "console.x.ai → API keys"),
            AiTemplate("OpenRouter", AiProtocol.OPENAI_COMPAT, "https://openrouter.ai/api/v1", "openrouter/auto", "openrouter.ai → Keys"),
            AiTemplate("OpenCode Zen", AiProtocol.OPENAI_COMPAT, "https://opencode.ai/zen/v1", "", "opencode.ai → Zen panosu"),
            AiTemplate("Ollama (yerel ağ)", AiProtocol.OPENAI_COMPAT, "http://192.168.1.100:11434/v1", "", null),
            AiTemplate("Özel (boş)", AiProtocol.OPENAI_COMPAT, "", "", null),
        )
    }
}

data class AiChatMessage(val role: String, val content: String) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

/** One entry of a provider's live model list ("Modelleri Getir"). */
data class AiModelInfo(val id: String, val displayName: String?)

/** Thrown by providers with a message safe to show in the chat UI. */
class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * v1.2 error style: friendly Turkish first line, then the exact failure (HTTP status +
 * a snippet of the server's own error body) so a wrong key vs. wrong URL vs. wrong
 * model is distinguishable without guesswork.
 */
object AiErrors {
    fun http(code: Int, body: String?): AiException {
        val friendly = when (code) {
            401, 403 -> "API anahtarı reddedildi — bu profilin anahtarını kontrol et."
            404 -> "Uç nokta bulunamadı — taban URL veya model adı hatalı olabilir."
            429 -> "Hız/kota sınırına takıldık — biraz bekleyip tekrar dene."
            in 500..599 -> "Sunucu tarafında sorun var — birazdan tekrar dene."
            else -> "Sunucu hata döndürdü."
        }
        val detail = body?.trim()?.replace(Regex("\\s+"), " ")?.take(200)
        return AiException(buildString {
            append(friendly)
            append(" (HTTP ").append(code)
            if (!detail.isNullOrBlank()) append(": ").append(detail)
            append(")")
        })
    }

    fun network(e: Exception): AiException = AiException(
        "Bağlantı kurulamadı — internet var mı? Yerel sunucu (Ollama) ise adres ve port doğru mu? " +
            "(${e.javaClass.simpleName}: ${e.message?.take(120) ?: "ayrıntı yok"})",
        e,
    )
}
