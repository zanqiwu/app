package io.agents.pokeclaw.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReplyUpdaterTest {

    @Test
    fun `stream chunks update one stable assistant bubble`() {
        val messages = mutableListOf(
            ChatMessage(
                role = ChatMessage.Role.ASSISTANT,
                content = "...",
                timestamp = 42L,
                isStreaming = true,
            )
        )

        assertTrue(replaceAssistantReply(messages, 42L, "前五个字", "mimo", true))
        assertTrue(replaceAssistantReply(messages, 42L, "前五个字和后续内容", "mimo", true))
        assertTrue(replaceAssistantReply(messages, 42L, "完整结果", "mimo", false))

        assertEquals(1, messages.size)
        assertEquals(42L, messages.single().timestamp)
        assertEquals("完整结果", messages.single().content)
        assertFalse(messages.single().isStreaming)
    }

    @Test
    fun `stale stream chunk cannot create another bubble`() {
        val messages = mutableListOf(
            ChatMessage(ChatMessage.Role.ASSISTANT, "新回答", timestamp = 100L)
        )

        assertFalse(replaceAssistantReply(messages, 42L, "旧请求片段", "mimo", true))
        assertEquals(1, messages.size)
        assertEquals("新回答", messages.single().content)
    }
}
