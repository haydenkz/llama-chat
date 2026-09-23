package com.llamacpp.mobile.domain.model

import kotlinx.serialization.Serializable

enum class ChatRole(val wire: String) {
    System("system"),
    User("user"),
    Assistant("assistant"),
    Tool("tool");

    companion object {
        fun fromWire(value: String?): ChatRole =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) } ?: Assistant
    }
}

/** A model-requested function call (e.g. `web_search`). */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ChatMessage(
    val id: Long = 0L,
    val conversationId: String = "",
    val role: ChatRole,
    val content: String = "",
    val reasoning: String = "",
    /** Data URIs (`data:image/png;base64,...`) for multimodal turns. */
    val images: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Model that produced an assistant message, for the stats line. */
    val model: String? = null,
    val tokenCount: Int? = null,
    val durationMs: Double? = null,
    val promptTokenCount: Int? = null,
    val tokensPerSecond: Double? = null,
    /** Tool calls requested by an assistant message. */
    val toolCalls: List<ToolCall> = emptyList(),
    /** For [ChatRole.Tool] results: the call they answer. */
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** One-line summary of the model's reasoning for this turn. */
    val thinkingSummary: String? = null,
    val error: String? = null,
)

data class Conversation(
    val id: String,
    val serverId: String,
    val title: String = "New chat",
    val model: String? = null,
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
