package com.yks2027.tracker.core.ai

import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * PRD §8 — one OpenAI-compatible chat-completions client covering OpenAI, Gemini
 * (compat endpoint), xAI/Grok, OpenRouter, OpenCode Zen, and Ollama (LAN; no key).
 * SSE streaming parsed by hand. Never used for Claude — that goes through the official
 * Anthropic SDK. v1.2 adds GET /models (test connection + live model discovery).
 */
@Singleton
class OpenAiCompatProvider @Inject constructor() {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun streamChat(
        apiKey: String,
        baseUrl: String,
        model: String,
        system: String,
        history: List<AiChatMessage>,
    ): Flow<String> = flow {
        val payload = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", system)
                }
                history.forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    }
                }
            }
        }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/chat/completions")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
            .build()

        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = runCatching { response.body?.string() }.getOrNull()
                    throw AiErrors.http(response.code, errorBody)
                }
                val source = response.body?.source() ?: throw AiException("Boş yanıt geldi.")
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val deltaText = runCatching {
                        json.parseToJsonElement(data).jsonObject["choices"]
                            ?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("delta")?.jsonObject
                            ?.get("content")?.jsonPrimitive?.contentOrNull
                    }.getOrNull()
                    if (!deltaText.isNullOrEmpty()) emit(deltaText)
                }
            }
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiErrors.network(e)
        } catch (e: Exception) {
            throw AiException("AI isteği başarısız: ${e.message?.take(200) ?: e.javaClass.simpleName}", e)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * GET {base}/models — the cheap connectivity probe and the "Modelleri Getir" source.
     * For Ollama this lists locally pulled models. Gemini's compat layer prefixes ids
     * with "models/"; that prefix is stripped so ids drop straight into chat requests.
     */
    suspend fun listModels(apiKey: String, baseUrl: String): List<AiModelInfo> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/models")
                .get()
                .apply { if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey") }
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    val body = runCatching { response.body?.string() }.getOrNull()
                    if (!response.isSuccessful) throw AiErrors.http(response.code, body)
                    val root = json.parseToJsonElement(body ?: "{}").jsonObject
                    val entries = root["data"]?.jsonArray ?: root["models"]?.jsonArray
                        ?: throw AiException("Model listesi beklenen biçimde değil (data alanı yok).")
                    entries.mapNotNull { element ->
                        val obj = element.jsonObject
                        val rawId = obj["id"]?.jsonPrimitive?.contentOrNull
                            ?: obj["name"]?.jsonPrimitive?.contentOrNull
                            ?: return@mapNotNull null
                        AiModelInfo(
                            id = rawId.removePrefix("models/"),
                            displayName = obj["display_name"]?.jsonPrimitive?.contentOrNull,
                        )
                    }
                }
            } catch (e: AiException) {
                throw e
            } catch (e: IOException) {
                throw AiErrors.network(e)
            } catch (e: Exception) {
                throw AiException("Model listesi alınamadı: ${e.message?.take(200) ?: e.javaClass.simpleName}", e)
            }
        }
}
