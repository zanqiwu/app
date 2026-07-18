package com.opendroid.ai.core.memory

import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.repository.ConversationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodicMemory @Inject constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend fun storeMessage(message: ChatMessage) {
        conversationRepository.insertMessage(message)
    }

    suspend fun searchLogs(query: String): List<ChatMessage> {
        return conversationRepository.getLastMessages(100).filter {
            it.text.contains(query, ignoreCase = true)
        }
    }
}
