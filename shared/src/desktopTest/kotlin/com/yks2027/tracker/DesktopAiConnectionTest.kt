package com.yks2027.tracker

import com.yks2027.tracker.core.ai.AiClient
import com.yks2027.tracker.core.ai.AiException
import com.yks2027.tracker.core.database.AiProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * "Bağlantıyı Sına" against REAL endpoints from the desktop JVM (spec verification bar):
 * a bogus key must come back as a friendly Turkish message + HTTP 401 + the server's body
 * on both protocols (OkHttp path and the Anthropic SDK path). Requires network.
 */
class DesktopAiConnectionTest {

    private fun probe(profile: AiProfileEntity) {
        DesktopTestApp.ensureStarted()
        val client = GlobalContext.get().get<AiClient>()
        try {
            runBlocking { client.listModels(profile, keyOverride = "sk-test-desktop-invalid-key") }
            fail("a bogus key must be rejected")
        } catch (e: AiException) {
            println("[test-connection] ${profile.name}: ${e.message}")
            assertTrue(e.message!!.contains("HTTP 401"))
            assertTrue(e.message!!.contains("anahtar", ignoreCase = true))
        }
    }

    @Test
    fun openAiCompatEndpointReports401WithDetail() =
        probe(AiProfileEntity(id = 0, name = "OpenAI", protocol = "OPENAI_COMPAT", baseUrl = "https://api.openai.com/v1", model = "gpt-5-mini", createdAt = 0))

    @Test
    fun anthropicSdkEndpointReports401WithDetail() =
        probe(AiProfileEntity(id = 0, name = "Claude", protocol = "ANTHROPIC", baseUrl = "https://api.anthropic.com", model = "claude-opus-5", createdAt = 0))
}
