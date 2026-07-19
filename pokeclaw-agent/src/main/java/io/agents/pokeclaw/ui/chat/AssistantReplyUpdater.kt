// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

internal fun replaceAssistantReply(
    messages: MutableList<ChatMessage>,
    replyTimestamp: Long,
    text: String,
    modelName: String,
    isStreaming: Boolean,
): Boolean {
    val index = messages.indexOfLast {
        it.role == ChatMessage.Role.ASSISTANT && it.timestamp == replyTimestamp
    }
    if (index < 0) return false

    messages[index] = messages[index].copy(
        content = text,
        modelName = modelName,
        isStreaming = isStreaming,
    )
    return true
}
