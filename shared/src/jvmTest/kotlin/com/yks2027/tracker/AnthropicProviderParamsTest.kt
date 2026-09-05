package com.yks2027.tracker

import com.yks2027.tracker.core.ai.AiChatMessage
import com.yks2027.tracker.core.ai.AnthropicProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v2.1 — web search attaches Anthropic's server-side tool only when enabled (no network needed). */
class AnthropicProviderParamsTest {
    private val history = listOf(AiChatMessage(role = AiChatMessage.ROLE_USER, content = "ÖSYM takvimi açıklandı mı?"))

    @Test
    fun webSearchOffSendsNoTools() {
        val p = AnthropicProvider().buildParams("claude-opus-5", "sys", history, webSearch = false)
        assertTrue(p.tools().map { it.isEmpty() }.orElse(true))
        assertEquals("sys", p.system().get().string().get())
    }

    @Test
    fun webSearchOnAttachesOneCappedServerTool() {
        val p = AnthropicProvider().buildParams("claude-opus-5", "sys", history, webSearch = true)
        val tools = p.tools().get()
        assertEquals(1, tools.size)
        assertTrue(tools.single().isWebSearchTool20260318())
        assertEquals(AnthropicProvider.WEB_SEARCH_MAX_USES, tools.single().asWebSearchTool20260318().maxUses().get())
    }
}
