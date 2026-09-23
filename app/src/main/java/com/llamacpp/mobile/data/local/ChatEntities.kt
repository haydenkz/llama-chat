package com.llamacpp.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.Conversation
import com.llamacpp.mobile.domain.model.ToolCall
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val title: String,
    val model: String?,
    val systemPrompt: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val conversationId: String,
    val role: String,
    val content: String,
    val reasoning: String,
    val imagesJson: String,
    val createdAt: Long,
    val model: String?,
    val tokenCount: Int?,
    val durationMs: Double?,
    val promptTokenCount: Int?,
    val tokensPerSecond: Double?,
    val toolCallsJson: String,
    val toolCallId: String?,
    val toolName: String?,
    val thinkingSummary: String?,
    val error: String?,
)

private val stringListSerializer = ListSerializer(String.serializer())
private val toolCallListSerializer = ListSerializer(ToolCall.serializer())

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    serverId = serverId,
    title = title,
    model = model,
    systemPrompt = systemPrompt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Conversation.toEntity(): ConversationEntity = ConversationEntity(
    id = id,
    serverId = serverId,
    title = title,
    model = model,
    systemPrompt = systemPrompt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun MessageEntity.toDomain(json: Json): ChatMessage = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = ChatRole.fromWire(role),
    content = content,
    reasoning = reasoning,
    images = runCatching { json.decodeFromString(stringListSerializer, imagesJson) }.getOrDefault(emptyList()),
    createdAt = createdAt,
    model = model,
    tokenCount = tokenCount,
    durationMs = durationMs,
    promptTokenCount = promptTokenCount,
    tokensPerSecond = tokensPerSecond,
    toolCalls = runCatching { json.decodeFromString(toolCallListSerializer, toolCallsJson) }.getOrDefault(emptyList()),
    toolCallId = toolCallId,
    toolName = toolName,
    thinkingSummary = thinkingSummary,
    error = error,
)

fun ChatMessage.toEntity(json: Json): MessageEntity = MessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.wire,
    content = content,
    reasoning = reasoning,
    imagesJson = json.encodeToString(stringListSerializer, images),
    createdAt = createdAt,
    model = model,
    tokenCount = tokenCount,
    durationMs = durationMs,
    promptTokenCount = promptTokenCount,
    tokensPerSecond = tokensPerSecond,
    toolCallsJson = json.encodeToString(toolCallListSerializer, toolCalls),
    toolCallId = toolCallId,
    toolName = toolName,
    thinkingSummary = thinkingSummary,
    error = error,
)
