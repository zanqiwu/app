package io.agents.pokeclaw.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class FinishSummaryStreamDecoderTest {

    @Test
    fun `streams finish summary from delta fragments`() {
        val decoder = FinishSummaryStreamDecoder()

        assertEquals("", decoder.accept(0, "fin", "{\"sum"))
        assertEquals("", decoder.accept(0, "ish", "mary\":\""))
        assertEquals("你好", decoder.accept(0, null, "你好"))
        assertEquals("\n世界", decoder.accept(0, null, "\\n世界\"}"))
    }

    @Test
    fun `streams finish summary from cumulative fragments without duplicates`() {
        val decoder = FinishSummaryStreamDecoder()

        assertEquals("你", decoder.accept(1, "finish", "{\"summary\":\"你"))
        assertEquals("好", decoder.accept(1, "finish", "{\"summary\":\"你好"))
        assertEquals("", decoder.accept(1, "finish", "{\"summary\":\"你好\"}"))
    }

    @Test
    fun `ignores other tool arguments`() {
        val decoder = FinishSummaryStreamDecoder()

        assertEquals("", decoder.accept(2, "tap", "{\"x\":100}"))
    }
}
