// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

data class TokenUsageSummary(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
)

internal fun appendTokenUsage(
    responseText: String,
    usage: TokenUsageSummary?,
): String {
    val footer = if (usage == null) {
        "本次用量：服务端未返回 usage"
    } else {
        "本次用量：输入 ${usage.inputTokens} · 输出 ${usage.outputTokens} · 合计 ${usage.totalTokens} tokens"
    }
    return "${responseText.trimEnd()}\n\n$footer"
}
