package com.opendroid.ai.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.agent.AgentLoop
import com.opendroid.ai.core.agent.AgentState
import com.opendroid.ai.data.models.ChatMessage
import com.opendroid.ai.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentLoop: AgentLoop,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    val conversationHistory: StateFlow<List<ChatMessage>> = conversationRepository.conversations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val agentState: StateFlow<AgentState> = agentLoop.agentState

    fun sendMessage(query: String, context: Context) {
        if (query.isBlank()) return
        agentLoop.processQuery(query, context)
    }

    fun approvePlan(context: Context) {
        agentLoop.approveProposedPlan(context)
    }

    fun rejectPlan() {
        agentLoop.rejectProposedPlan()
    }

    fun clearChat() {
        viewModelScope.launch {
            conversationRepository.clearAll()
        }
    }
}
