// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.ui.chat

import java.text.NumberFormat
import java.util.Locale

data class TokenUsageSummary(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int,
    val rounds: Int? = null,
)

data class TokenUsageContent(
    val responseText: String,
    val footer: String?,
)

private const val USAGE_PREFIX = "本次用量："

internal fun appendTokenUsage(
    responseText: String,
    usage: TokenUsageSummary?,
): String {
    val footer = if (usage == null) {
        "${USAGE_PREFIX}服务端未返回 usage"
    } else {
        val formatter = NumberFormat.getIntegerInstance(Locale.US)
        val scope = usage.rounds?.let { "Agent $it 轮累计" } ?: "单次调用"
        "$USAGE_PREFIX$scope · 输入 ${formatter.format(usage.inputTokens)} · " +
            "输出 ${formatter.format(usage.outputTokens)} · 合计 ${formatter.format(usage.totalTokens)} tokens"
    }
    return "${responseText.trimEnd()}\n\n$footer"
}

internal fun splitTokenUsage(text: String): TokenUsageContent {
    val marker = "\n\n$USAGE_PREFIX"
    val markerIndex = text.lastIndexOf(marker)
    if (markerIndex < 0) return TokenUsageContent(text, null)

    return TokenUsageContent(
        responseText = text.substring(0, markerIndex).trimEnd(),
        footer = text.substring(markerIndex + 2).trim(),
    )
}

internal fun stripTokenUsage(text: String): String = splitTokenUsage(text).responseText
