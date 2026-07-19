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

        assertEquals("完成\n\n本次用量：单次调用 · 输入 120 · 输出 30 · 合计 150 tokens", result)
    }

    @Test
    fun `does not invent token count when provider omits usage`() {
        val result = appendTokenUsage("完成", usage = null)

        assertEquals("完成\n\n本次用量：服务端未返回 usage", result)
    }

    @Test
    fun `formats accumulated agent rounds and separates footer`() {
        val result = appendTokenUsage(
            responseText = "## 完成",
            usage = TokenUsageSummary(
                inputTokens = 12_524,
                outputTokens = 609,
                totalTokens = 13_133,
                rounds = 3,
            ),
        )

        val content = splitTokenUsage(result)
        assertEquals("## 完成", content.responseText)
        assertEquals(
            "本次用量：Agent 3 轮累计 · 输入 12,524 · 输出 609 · 合计 13,133 tokens",
            content.footer,
        )
    }
}
