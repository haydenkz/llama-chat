package com.llamacpp.mobile.data.tools

import com.llamacpp.mobile.data.remote.dto.ToolDefinitionDto
import com.llamacpp.mobile.data.remote.dto.ToolDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private val argJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Reads a string argument from a tool-call arguments JSON, trying several keys. */
internal fun toolStringArg(arguments: String, vararg keys: String): String? {
    val obj = runCatching { argJson.parseToJsonElement(arguments).jsonObject }.getOrNull()
    if (obj != null) {
        for (key in keys) {
            val value = obj[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            if (!value.isNullOrBlank()) return value
        }
    }
    // Fallback for slightly malformed JSON a model may emit.
    for (key in keys) {
        val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(arguments) ?: continue
        val value = match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        if (value.isNotBlank()) return value
    }
    return null
}

/** Builds an OpenAI function tool whose parameters are all required strings. */
internal fun functionTool(name: String, description: String, params: Map<String, String>): ToolDto =
    ToolDto(
        type = "function",
        function = ToolDefinitionDto(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    params.forEach { (key, desc) ->
                        putJsonObject(key) {
                            put("type", "string")
                            put("description", desc)
                        }
                    }
                }
                put("required", JsonArray(params.keys.map { JsonPrimitive(it) }))
            },
        ),
    )
