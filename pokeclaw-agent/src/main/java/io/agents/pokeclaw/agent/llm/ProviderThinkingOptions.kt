// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import io.agents.pokeclaw.agent.AgentConfig

internal fun providerThinkingParameters(config: AgentConfig): Map<String, Any> {
    val isMiMo = config.baseUrl.contains("xiaomimimo.com", ignoreCase = true)
    val isDeepSeek = config.baseUrl.contains("deepseek.com", ignoreCase = true) ||
        config.modelName.contains("deepseek", ignoreCase = true)
    if (!isMiMo && !isDeepSeek) return emptyMap()

    val type = if (config.thinkingEnabled) "enabled" else "disabled"
    return mapOf("thinking" to mapOf("type" to type))
}
