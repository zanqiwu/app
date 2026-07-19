package io.agents.pokeclaw.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenMonitorTest {

    @Test
    fun `accumulates server usage across agent rounds`() {
        val monitor = TokenMonitor("mimo-v2-flash")

        monitor.record(step = 1, inputTokens = 100, outputTokens = 20, totalTokenCount = 120)
        monitor.record(step = 2, inputTokens = 160, outputTokens = 30, totalTokenCount = 190)

        val status = monitor.getStatus()
        assertTrue(status.hasServerUsage)
        assertEquals(260, status.inputTokens)
        assertEquals(50, status.outputTokens)
        assertEquals(310, status.totalTokens)
    }

    @Test
    fun `marks missing usage without inventing token counts`() {
        val monitor = TokenMonitor("mimo-v2-flash")

        monitor.record(step = 1, inputTokens = null, outputTokens = null, totalTokenCount = null)

        val status = monitor.getStatus()
        assertFalse(status.hasServerUsage)
        assertEquals(0, status.totalTokens)
    }
}
