// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

/** Extracts incremental text from a streamed finish(summary=...) tool call. */
internal class FinishSummaryStreamDecoder {
    private val toolNames = mutableMapOf<Int, String>()
    private val argumentBuffers = mutableMapOf<Int, StringBuilder>()
    private val emittedLengths = mutableMapOf<Int, Int>()

    fun accept(index: Int, name: String?, partialArguments: String): String {
        name?.takeIf { it.isNotBlank() }?.let { fragment ->
            val existing = toolNames[index].orEmpty()
            toolNames[index] = if (fragment.startsWith(existing)) fragment else existing + fragment
        }
        val buffer = argumentBuffers.getOrPut(index) { StringBuilder() }
        if (partialArguments.startsWith(buffer.toString())) {
            buffer.clear()
            buffer.append(partialArguments)
        } else {
            buffer.append(partialArguments)
        }
        if (!toolNames[index].equals("finish", ignoreCase = true)) return ""

        val summary = extractJsonStringPrefix(buffer.toString(), "summary") ?: return ""
        val emittedLength = emittedLengths[index] ?: 0
        if (summary.length <= emittedLength) return ""
        emittedLengths[index] = summary.length
        return summary.substring(emittedLength)
    }

    private fun extractJsonStringPrefix(json: String, key: String): String? {
        val keyMatch = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"").find(json) ?: return null
        var index = keyMatch.range.last + 1
        val result = StringBuilder()
        var escaping = false

        while (index < json.length) {
            val char = json[index++]
            if (escaping) {
                when (char) {
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    '\\', '/', '"' -> result.append(char)
                    'u' -> {
                        if (index + 4 > json.length) return result.toString()
                        val code = json.substring(index, index + 4).toIntOrNull(16) ?: return result.toString()
                        result.append(code.toChar())
                        index += 4
                    }
                    else -> result.append(char)
                }
                escaping = false
            } else {
                when (char) {
                    '\\' -> escaping = true
                    '"' -> return result.toString()
                    else -> result.append(char)
                }
            }
        }
        return result.toString()
    }
}
