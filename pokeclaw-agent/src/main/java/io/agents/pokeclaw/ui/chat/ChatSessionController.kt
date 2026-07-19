// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import io.agents.pokeclaw.agent.ModelPricing
import io.agents.pokeclaw.agent.llm.LlmClient
import io.agents.pokeclaw.agent.llm.LlmResponse
import io.agents.pokeclaw.agent.llm.LlmSessionManager
import io.agents.pokeclaw.agent.llm.ModelConfigRepository
import io.agents.pokeclaw.agent.llm.StreamingListener
import io.agents.pokeclaw.utils.KVUtils
import io.agents.pokeclaw.utils.XLog
import java.util.concurrent.ExecutorService

data class ChatSessionUiState(
    val messages: SnapshotStateList<ChatMessage>,
    val modelStatus: MutableState<String>,
    val isAwaitingReply: MutableState<Boolean>,
    val inputEnabled: MutableState<Boolean>,
    val isDownloading: MutableState<Boolean>,
    val downloadProgress: MutableState<Int>,
    val sessionTokens: MutableState<Int>,
    val sessionCost: MutableState<Double>,
)

/** Cloud-only chat runtime used by the embedded heavy build. */
class ChatSessionController(
    private val activity: ComponentActivity,
    private val executor: ExecutorService,
    private val uiState: ChatSessionUiState,
    private val onPersistConversation: () -> Unit,
    private val onRefreshSidebarHistory: () -> Unit,
    private val isTaskRunning: () -> Boolean,
    private val onReplyCompleted: () -> Unit = {},
) {
    companion object {
        private const val TAG = "ChatSessionController"
        private const val MAX_RESPONSE_CHARS = 50_000
        private const val STREAM_UPDATE_INTERVAL_MS = 50L
        private const val BASE_SYSTEM_PROMPT =
            "你是运行在 Android 手机上的智能助手。除非用户明确要求其他语言，否则始终使用简体中文回答。"
    }

    private var cloudClient: LlmClient? = null
    private var cloudModelName: String? = null
    private val cloudHistory = mutableListOf<dev.langchain4j.data.message.ChatMessage>()
    private var isModelReady = false
    private var suppressNextCloudSwitchMessage = false

    fun isModelReady(): Boolean = isModelReady

    fun loadModelIfReady(
        conversationId: String? = null,
        visibleMessages: List<ChatMessage> = emptyList(),
    ) {
        val cloud = ModelConfigRepository.snapshot().activeCloud
        if (!cloud.isConfigured) {
            cloudClient = null
            cloudModelName = null
            isModelReady = false
            uiState.modelStatus.value = "请先配置云端模型"
            setButtonsEnabled(false)
            return
        }

        val previousModel = cloudModelName
        cloudClient = LlmSessionManager.createCloudClient(temperature = 0.7)
        if (cloudClient == null) {
            isModelReady = false
            uiState.modelStatus.value = "云端模型配置无效"
            setButtonsEnabled(false)
            return
        }

        cloudModelName = cloud.modelName
        if (previousModel == null || cloudHistory.isEmpty()) {
            rebuildCloudHistory(visibleMessages)
        } else if (previousModel != cloud.modelName) {
            cloudHistory.add(SystemMessage.from("用户已将模型从 $previousModel 切换为 ${cloud.modelName}，请自然地继续对话。"))
            if (suppressNextCloudSwitchMessage) {
                suppressNextCloudSwitchMessage = false
            } else {
                addSystem("已切换到 ${cloud.modelName}")
            }
        }
        isModelReady = true
        uiState.modelStatus.value = "${cloud.modelName} · 云端"
        uiState.isDownloading.value = false
        setButtonsEnabled(!isTaskRunning())
        XLog.i(TAG, "Cloud chat ready: ${cloud.modelName} via ${cloud.resolvedBaseUrl}")
    }

    fun onResume(conversationId: String, visibleMessages: List<ChatMessage>) {
        loadModelIfReady(conversationId, visibleMessages)
    }

    fun onPause(conversationId: String) = Unit

    fun onDestroy() {
        cloudClient?.close()
        cloudClient = null
    }

    fun releaseForTask() {
        setButtonsEnabled(false)
    }

    fun prepareForTaskStart() {
        setButtonsEnabled(false)
    }

    fun sendChat(text: String) {
        val client = cloudClient
        if (client == null || !isModelReady) {
            addSystem("请先在配置中填写云端模型的 API。")
            return
        }

        addUser(text)
        uiState.isAwaitingReply.value = true
        uiState.messages.add(ChatMessage(ChatMessage.Role.ASSISTANT, "..."))

        executor.submit {
            try {
                ensureCloudHistoryInitialized()
                cloudHistory.add(UserMessage.from(text))
                val streamedText = StringBuilder()
                var lastUiUpdate = 0L
                val response = if (KVUtils.isStreamingEnabled()) {
                    client.chatStreaming(
                        cloudHistory,
                        emptyList(),
                        object : StreamingListener {
                        override fun onPartialText(token: String) {
                            if (streamedText.length < MAX_RESPONSE_CHARS) {
                                streamedText.append(token.take(MAX_RESPONSE_CHARS - streamedText.length))
                            }
                            val now = android.os.SystemClock.uptimeMillis()
                            if (now - lastUiUpdate >= STREAM_UPDATE_INTERVAL_MS) {
                                lastUiUpdate = now
                                val snapshot = streamedText.toString()
                                postToMain { replaceTypingIndicator(snapshot) }
                            }
                        }

                        override fun onComplete(response: LlmResponse) = Unit

                        override fun onError(error: Throwable) = Unit
                        }
                    )
                } else {
                    client.chat(cloudHistory, emptyList())
                }
                val responseText = (response.text ?: streamedText.toString()).take(MAX_RESPONSE_CHARS)
                    .ifBlank { "（模型没有返回文本）" }
                cloudHistory.add(AiMessage.from(responseText))
                val usage = response.tokenUsage
                val usageSummary = usage?.let {
                    val inputTokens = it.inputTokenCount()
                    val outputTokens = it.outputTokenCount()
                    val totalTokens = it.totalTokenCount()
                    if (inputTokens == null && outputTokens == null && totalTokens == null) {
                        null
                    } else {
                        TokenUsageSummary(
                            inputTokens = inputTokens ?: 0,
                            outputTokens = outputTokens ?: 0,
                            totalTokens = totalTokens ?: ((inputTokens ?: 0) + (outputTokens ?: 0)),
                        )
                    }
                }
                val displayText = appendTokenUsage(responseText, usageSummary)
                val fallbackModel = cloudModelName ?: ModelConfigRepository.snapshot().activeCloud.modelName
                val modelTag = response.modelName ?: fallbackModel
                postToMain {
                    replaceTypingIndicator(displayText, modelTag)
                    uiState.isAwaitingReply.value = false
                    if (usageSummary != null) {
                        uiState.sessionTokens.value += usageSummary.totalTokens
                        uiState.sessionCost.value += ModelPricing.estimateCost(
                            modelTag,
                            usageSummary.inputTokens,
                            usageSummary.outputTokens,
                        )
                    }
                    onPersistConversation()
                    onReplyCompleted()
                }
            } catch (e: Exception) {
                XLog.e(TAG, "Cloud chat error", e)
                postToMain {
                    replaceTypingIndicator("请求失败：${e.message ?: "未知错误"}")
                    uiState.isAwaitingReply.value = false
                }
            }
        }
    }

    fun switchModel(modelId: String, displayName: String) {
        if (modelId == "NONE" || modelId == "LOCAL") {
            uiState.modelStatus.value = "请先配置云端模型"
            isModelReady = false
            setButtonsEnabled(false)
            return
        }
        ModelConfigRepository.activateCloudSelection(modelId)
        suppressNextCloudSwitchMessage = true
        loadModelIfReady()
        addSystem("已切换到 $displayName")
    }

    fun startNewConversationRuntime() {
        cloudHistory.clear()
        cloudHistory.add(SystemMessage.from(BASE_SYSTEM_PROMPT))
        postToMain {
            addSystem("已开始新对话。")
            onRefreshSidebarHistory()
        }
    }

    fun restoreConversationRuntime(conversationId: String, messages: List<ChatMessage>) {
        rebuildCloudHistory(messages)
    }

    fun syncUiToActiveModel() {
        loadModelIfReady(visibleMessages = uiState.messages)
    }

    private fun rebuildCloudHistory(messages: List<ChatMessage>) {
        cloudHistory.clear()
        cloudHistory.add(SystemMessage.from(BASE_SYSTEM_PROMPT))
        messages.forEach { message ->
            when (message.role) {
                ChatMessage.Role.USER -> cloudHistory.add(UserMessage.from(message.content))
                ChatMessage.Role.ASSISTANT -> if (message.content != "...") {
                    cloudHistory.add(AiMessage.from(message.content))
                }
                else -> Unit
            }
        }
    }

    private fun ensureCloudHistoryInitialized() {
        if (cloudHistory.isEmpty()) rebuildCloudHistory(uiState.messages)
    }

    private fun replaceTypingIndicator(text: String, actualModelName: String? = null) {
        val modelTag = actualModelName ?: cloudModelName.orEmpty()
        val index = uiState.messages.indexOfLast {
            it.role == ChatMessage.Role.ASSISTANT && (it.content == "..." || uiState.isAwaitingReply.value)
        }
        val message = ChatMessage(ChatMessage.Role.ASSISTANT, text, modelName = modelTag)
        if (index >= 0) uiState.messages[index] = message else uiState.messages.add(message)
    }

    private fun addUser(text: String) {
        uiState.messages.add(ChatMessage(ChatMessage.Role.USER, text))
    }

    private fun addSystem(text: String) {
        val last = uiState.messages.lastOrNull()
        if (last?.role != ChatMessage.Role.SYSTEM || !last.content.equals(text, ignoreCase = true)) {
            uiState.messages.add(ChatMessage(ChatMessage.Role.SYSTEM, text))
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        uiState.inputEnabled.value = enabled
    }

    private fun postToMain(action: () -> Unit) {
        activity.runOnUiThread(action)
    }
}
