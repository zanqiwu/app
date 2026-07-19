package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenUsageFormatterTest {

    @Test
    fun `appends server token usage after response`() {
        val result = appendTokenUsage(
            responseText = "完成",
            usage = TokenUsageSummary(inputTokens = 120, outputTokens = 30, totalTokens = 150),
        )

        assertEquals("完成\n\n本次用量：输入 120 · 输出 30 · 合计 150 tokens", result)
    }

    @Test
    fun `does not invent token count when provider omits usage`() {
        val result = appendTokenUsage("完成", usage = null)

        assertEquals("完成\n\n本次用量：服务端未返回 usage", result)
    }
}
