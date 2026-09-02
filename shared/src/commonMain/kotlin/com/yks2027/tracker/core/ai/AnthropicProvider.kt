package com.yks2027.tracker.core.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * PRD §8 — Claude via the official Anthropic Java SDK (Kotlin uses the Java SDK;
 * OkHttp-based, Android-compatible). Streaming; thinking left at the model default
 * (adaptive on claude-opus-5). A `refusal` stop is surfaced as a friendly message —
 * server-side fallbacks are deliberately not wired into this mobile client.
 *
 * v1.2: the base URL comes from the profile (editable for proxies; the SDK builder's
 * baseUrl() override is used), and the Models API backs "Bağlantıyı Sına" +
 * "Modelleri Getir".
 */
@Singleton
class AnthropicProvider @Inject constructor() {

    private fun client(apiKey: String, baseUrl: String?): AnthropicClient =
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { baseUrl?.takeIf { it.isNotBlank() }?.let { baseUrl(it.trimEnd('/')) } }
            .build()

    fun streamChat(
        apiKey: String,
        baseUrl: String?,
        model: String,
        system: String,
        history: List<AiChatMessage>,
    ): Flow<String> = flow {
        val client = client(apiKey, baseUrl)
        try {
            val builder = MessageCreateParams.builder()
                .model(model)
                .maxTokens(2048L)
                .system(system)
            history.forEach { message ->
                when (message.role) {
                    AiChatMessage.ROLE_ASSISTANT -> builder.addAssistantMessage(message.content)
                    else -> builder.addUserMessage(message.content)
                }
            }
            var refused = false
            client.messages().createStreaming(builder.build()).use { response ->
                val events = response.stream().iterator()
                while (events.hasNext()) {
                    val event = events.next()
                    val delta = event.contentBlockDelta().orElse(null)
                    val text = delta?.delta()?.text()?.orElse(null)?.text()
                    if (!text.isNullOrEmpty()) emit(text)
                    event.messageDelta().orElse(null)?.let { messageDelta ->
                        val stop = messageDelta.delta().stopReason().orElse(null)?.toString()
                        if (stop != null && stop.contains("refusal", ignoreCase = true)) refused = true
                    }
                }
            }
            if (refused) {
                throw AiException("Model bu isteği yanıtlamayı reddetti. Soruyu farklı ifade etmeyi dene.")
            }
        } catch (e: Exception) {
            throw mapError(e)
        } finally {
            runCatching { client.close() }
        }
    }.flowOn(Dispatchers.IO)

    /** Cheap connectivity check + live model list via the Models API. */
    suspend fun listModels(apiKey: String, baseUrl: String?): List<AiModelInfo> =
        withContext(Dispatchers.IO) {
            val client = client(apiKey, baseUrl)
            try {
                client.models().list().autoPager().take(200).map { info ->
                    AiModelInfo(id = info.id(), displayName = info.displayName())
                }
            } catch (e: Exception) {
                throw mapError(e)
            } finally {
                runCatching { client.close() }
            }
        }

    /**
     * v1.2 AI extraction: single non-streaming message with image/PDF/text content
     * blocks. Returns the model's raw text (the strict-JSON contract is parsed upstream).
     */
    suspend fun createOnce(
        apiKey: String,
        baseUrl: String?,
        model: String,
        system: String,
        blocks: List<com.anthropic.models.messages.ContentBlockParam>,
    ): String = withContext(Dispatchers.IO) {
        val client = client(apiKey, baseUrl)
        try {
            val params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1536L)
                .system(system)
                .addUserMessageOfBlockParams(blocks)
                .build()
            val message = client.messages().create(params)
            message.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        } catch (e: Exception) {
            throw mapError(e)
        } finally {
            runCatching { client.close() }
        }
    }

    private fun mapError(e: Exception): AiException = when (e) {
        is AiException -> e
        is AnthropicServiceException ->
            AiErrors.http(e.statusCode(), runCatching { e.body().toString() }.getOrNull())
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            AiErrors.network(e)
        else -> {
            // The SDK wraps transport errors; unwrap one level before giving up.
            val cause = e.cause
            if (cause is UnknownHostException || cause is ConnectException || cause is SocketTimeoutException) {
                AiErrors.network(cause as Exception)
            } else {
                AiException("Claude isteği başarısız: ${e.message?.take(200) ?: e.javaClass.simpleName}", e)
            }
        }
    }
}
