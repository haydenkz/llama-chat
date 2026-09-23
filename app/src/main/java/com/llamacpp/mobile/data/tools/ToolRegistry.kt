package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDto
import com.llamacpp.mobile.domain.model.ToolCall

/** The set of tools available to the model. */
class ToolRegistry(private val tools: List<ChatTool>) {

    fun all(): List<ChatTool> = tools

    fun byName(name: String): ChatTool? = tools.firstOrNull { it.name == name }

    fun definitions(enabled: Set<String>): List<ToolDto> =
        tools.filter { it.name in enabled }.map { it.definition() }

    suspend fun execute(call: ToolCall): String {
        val tool = byName(call.name)
            ?: return "Unknown tool \"${call.name}\". Available tools: ${tools.joinToString { it.name }}."
        return runCatching { tool.execute(call.arguments) }
            .getOrElse { "Tool \"${tool.displayName}\" failed: ${it.message}" }
    }
}
