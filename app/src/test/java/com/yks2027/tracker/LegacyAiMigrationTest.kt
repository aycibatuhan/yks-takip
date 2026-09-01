package com.yks2027.tracker

import com.yks2027.tracker.core.ai.AiProtocol
import com.yks2027.tracker.core.ai.LegacyAiMigration
import com.yks2027.tracker.core.datastore.LegacyAiSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.2 — single-slot (v1.0/v1.1) → ai_profiles migration decision logic. */
class LegacyAiMigrationTest {

    @Test
    fun freshInstallMigratesToNothing() {
        assertNull(LegacyAiMigration.specFor(LegacyAiSlot(null, null, null), hadKey = false))
    }

    @Test
    fun providerPickedButNoKeyAndKeyRequired_migratesToNothing() {
        // The v1.0 UI wrote ai_provider on selection; without a key it never worked.
        assertNull(
            LegacyAiMigration.specFor(
                LegacyAiSlot("OPENAI", "https://api.openai.com/v1", "gpt-5-mini"),
                hadKey = false,
            ),
        )
    }

    @Test
    fun anthropicWithKey_becomesAnthropicProfileWithDefaultBaseUrl() {
        val spec = LegacyAiMigration.specFor(
            LegacyAiSlot(providerName = null, baseUrl = null, model = "claude-opus-5"),
            hadKey = true,
        )!!
        assertEquals(AiProtocol.ANTHROPIC, spec.protocol)
        assertEquals("https://api.anthropic.com", spec.baseUrl)
        assertEquals("claude-opus-5", spec.model)
        assertEquals("Claude (Anthropic)", spec.name)
    }

    @Test
    fun openAiWithKey_keepsStoredBaseUrlAndModel() {
        val spec = LegacyAiMigration.specFor(
            LegacyAiSlot("OPENAI", "https://proxy.example.com/v1", "gpt-5-mini"),
            hadKey = true,
        )!!
        assertEquals(AiProtocol.OPENAI_COMPAT, spec.protocol)
        assertEquals("https://proxy.example.com/v1", spec.baseUrl)
        assertEquals("gpt-5-mini", spec.model)
    }

    @Test
    fun ollamaWithoutKey_stillMigrates() {
        // The one keyless provider: an explicit selection means it was in use.
        val spec = LegacyAiMigration.specFor(
            LegacyAiSlot("OLLAMA", "http://192.168.1.20:11434/v1", "llama3.1"),
            hadKey = false,
        )!!
        assertEquals(AiProtocol.OPENAI_COMPAT, spec.protocol)
        assertEquals("http://192.168.1.20:11434/v1", spec.baseUrl)
    }

    @Test
    fun unknownProviderNameWithKey_fallsBackToAnthropic() {
        val spec = LegacyAiMigration.specFor(
            LegacyAiSlot("SOME_FUTURE_PROVIDER", null, null),
            hadKey = true,
        )!!
        assertEquals(AiProtocol.ANTHROPIC, spec.protocol)
        assertEquals("claude-opus-5", spec.model)
    }

    @Test
    fun blankStoredValuesFallBackToProviderDefaults() {
        val spec = LegacyAiMigration.specFor(
            LegacyAiSlot("GEMINI", "", ""),
            hadKey = true,
        )!!
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai", spec.baseUrl)
        assertEquals("gemini-2.5-flash", spec.model)
    }
}
