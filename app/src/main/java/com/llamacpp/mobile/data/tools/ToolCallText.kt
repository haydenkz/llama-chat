package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.domain.model.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * Fallback for servers that hand a model's tool call back as plain text
 * (`<tool_call>{"name": …, "arguments": …}</tool_call>`, Hermes/Qwen/Granite
 * style) instead of a structured `tool_calls` delta — typically when the chat
 * template's parser does not recognise the model's output. The markup must never
 * reach the user as an "answer".
 */
object ToolCallText {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** A complete `<tool_call>…</tool_call>` block (optionally `<|tool_call|>`). */
    private val block = Regex("""<\|?tool_call\|?>\s*(.*?)\s*<\|?/tool_call\|?>""", RegexOption.DOT_MATCHES_ALL)

    /** An opening tag with no closing tag yet (still streaming). */
    private val openTail = Regex("""<\|?tool_call\|?>.*$""", RegexOption.DOT_MATCHES_ALL)

    class Extracted(val content: String, val toolCalls: List<ToolCall>)

    /** True once the text contains any tool-call markup, complete or not. */
    fun hasMarkup(content: String): Boolean = content.contains("<tool_call") || content.contains("<|tool_call|")

    /** Parses every complete block into [ToolCall]s and removes them from the text. */
    fun extract(content: String): Extracted {
        if (!content.contains("tool_call")) return Extracted(content, emptyList())
        val calls = ArrayList<ToolCall>()
        val cleaned = block.replace(content) { match ->
            calls += parseCalls(match.groupValues[1])
            ""
        }
        return Extracted(cleaned.trim(), calls)
    }

    /** Removes complete blocks and any unterminated trailing block, for live display. */
    fun strip(content: String): String {
        if (!content.contains("tool_call")) return content
        return openTail.replace(block.replace(content, ""), "").trimEnd()
    }

    private fun parseCalls(body: String): List<ToolCall> {
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        val objects = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(element)
            else -> emptyList()
        }
        return objects.mapNotNull { obj ->
            // Some templates nest the call under "function".
            val fn = (obj["function"] as? JsonObject) ?: obj
            val name = (fn["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val arguments: JsonElement? = fn["arguments"] ?: fn["parameters"]
            val argumentsText = when (arguments) {
                null -> "{}"
                is JsonPrimitive -> arguments.content
                else -> arguments.toString()
            }
            ToolCall(
                id = (obj["id"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                name = name,
                arguments = argumentsText,
            )
        }
    }
}
