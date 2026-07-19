package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.agent.AgentConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiMoRequestOptionsTest {

    @Test
    fun `disables MiMo thinking by default`() {
        val config = AgentConfig(
            apiKey = "test",
            baseUrl = "https://api.xiaomimimo.com/v1",
        )

        assertEquals(
            mapOf("thinking" to mapOf("type" to "disabled")),
            providerThinkingParameters(config),
        )
    }

    @Test
    fun `enables MiMo thinking when requested`() {
        val config = AgentConfig(
            apiKey = "test",
            baseUrl = "https://api.xiaomimimo.com/anthropic",
            thinkingEnabled = true,
        )

        assertEquals(
            mapOf("thinking" to mapOf("type" to "enabled")),
            providerThinkingParameters(config),
        )
    }

    @Test
    fun `does not send MiMo option to other providers`() {
        val config = AgentConfig(apiKey = "test", baseUrl = "https://api.openai.com/v1")

        assertTrue(providerThinkingParameters(config).isEmpty())
    }

    @Test
    fun `disables DeepSeek thinking with the same preference`() {
        val config = AgentConfig(
            apiKey = "test",
            baseUrl = "https://api.deepseek.com/v1",
            modelName = "deepseek-v4-flash",
        )

        assertEquals(
            mapOf("thinking" to mapOf("type" to "disabled")),
            providerThinkingParameters(config),
        )
    }
}
