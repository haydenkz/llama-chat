package com.llamacpp.mobile.data.repo

import com.llamacpp.mobile.data.local.ChatDao
import com.llamacpp.mobile.data.local.toDomain
import com.llamacpp.mobile.data.local.toEntity
import com.llamacpp.mobile.data.remote.dto.ChatCompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.ChatMessageDto
import com.llamacpp.mobile.data.remote.dto.ToolCallDto
import com.llamacpp.mobile.data.remote.dto.ToolDto
import com.llamacpp.mobile.data.remote.dto.ToolFunctionCallDto
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.Conversation
import com.llamacpp.mobile.domain.model.SamplerSettings
import com.llamacpp.mobile.domain.model.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private val dateTimeFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm z")

class ChatRepository(
    private val dao: ChatDao,
    private val json: Json,
) {
    fun conversations(serverId: String): Flow<List<Conversation>> =
        dao.conversations(serverId).map { list -> list.map { it.toDomain() } }

    fun messages(conversationId: String): Flow<List<ChatMessage>> =
        dao.messages(conversationId).map { list -> list.map { it.toDomain(json) } }

    suspend fun createConversation(
        serverId: String,
        model: String?,
        systemPrompt: String,
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            serverId = serverId,
            title = DEFAULT_TITLE,
            model = model,
            systemPrompt = systemPrompt,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertConversation(conversation.toEntity())
        return conversation
    }

    suspend fun renameConversation(id: String, title: String) =
        dao.renameConversation(id, title, System.currentTimeMillis())

    suspend fun conversation(id: String): Conversation? = dao.getConversation(id)?.toDomain()

    suspend fun deleteConversation(id: String) {
        dao.deleteMessages(id)
        dao.deleteConversation(id)
    }

    suspend fun setConversationModel(id: String, model: String?) =
        dao.setModel(id, model, System.currentTimeMillis())

    suspend fun setConversationSystemPrompt(id: String, prompt: String) =
        dao.setSystemPrompt(id, prompt, System.currentTimeMillis())

    suspend fun insertMessage(message: ChatMessage): Long {
        val rowId = dao.insertMessage(message.toEntity(json))
        dao.touch(message.conversationId, System.currentTimeMillis())
        return rowId
    }

    suspend fun updateMessage(message: ChatMessage) = dao.updateMessage(message.toEntity(json))

    suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    suspend fun deleteMessagesFrom(conversationId: String, fromId: Long) =
        dao.deleteMessagesFrom(conversationId, fromId)

    suspend fun autoTitleFromFirstMessage(conversationId: String, text: String) {
        val conversation = dao.getConversation(conversationId) ?: return
        if (conversation.title != DEFAULT_TITLE) return
        val title = text.trim().lineSequence().firstOrNull()?.take(48)?.trim().orEmpty()
        if (title.isNotBlank()) dao.renameConversation(conversationId, title, System.currentTimeMillis())
    }

    fun buildRequest(
        model: String?,
        systemPrompt: String,
        history: List<ChatMessage>,
        settings: SamplerSettings,
        tools: List<ToolDto>? = null,
    ): ChatCompletionRequestDto {
        val system = buildSystemPrompt(systemPrompt, toolsEnabled = !tools.isNullOrEmpty())

        val messages = buildList {
            if (system.isNotBlank()) {
                add(ChatMessageDto(ChatRole.System.wire, JsonPrimitive(system)))
            }
            history.filter { it.role != ChatRole.System }.forEach { message ->
                when (message.role) {
                    ChatRole.Tool -> add(
                        ChatMessageDto(
                            role = ChatRole.Tool.wire,
                            content = JsonPrimitive(message.content),
                            toolCallId = message.toolCallId,
                            name = message.toolName,
                        ),
                    )

                    else -> {
                        val toolCalls = message.toolCalls
                        val content = when {
                            message.images.isNotEmpty() -> contentFor(message)
                            message.content.isBlank() && toolCalls.isNotEmpty() -> null
                            else -> JsonPrimitive(message.content)
                        }
                        add(
                            ChatMessageDto(
                                role = message.role.wire,
                                content = content,
                                toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map { it.toDto() },
                            ),
                        )
                    }
                }
            }
        }
        return ChatCompletionRequestDto(
            model = model,
            messages = messages,
            stream = settings.stream,
            temperature = settings.temperature,
            dynatempRange = settings.dynatempRange,
            dynatempExponent = settings.dynatempExponent,
            topK = settings.topK,
            topP = settings.topP,
            minP = settings.minP,
            xtcProbability = settings.xtcProbability,
            xtcThreshold = settings.xtcThreshold,
            typicalP = settings.typicalP,
            repeatLastN = settings.repeatLastN,
            repeatPenalty = settings.repeatPenalty,
            presencePenalty = settings.presencePenalty,
            frequencyPenalty = settings.frequencyPenalty,
            dryMultiplier = settings.dryMultiplier,
            dryBase = settings.dryBase,
            dryAllowedLength = settings.dryAllowedLength,
            mirostat = settings.mirostat,
            mirostatTau = settings.mirostatTau,
            mirostatEta = settings.mirostatEta,
            maxTokens = settings.maxTokens,
            seed = settings.seed,
            stop = settings.stop.takeIf { it.isNotEmpty() },
            reasoning = settings.reasoning,
            cachePrompt = settings.cachePrompt,
            nKeep = settings.nKeep.takeIf { it > 0 },
            tools = tools,
            toolChoice = if (tools != null) "auto" else null,
        )
    }

    private fun ToolCall.toDto(): ToolCallDto = ToolCallDto(
        id = id,
        type = "function",
        function = ToolFunctionCallDto(name = name, arguments = arguments),
    )

    private fun contentFor(message: ChatMessage) =
        if (message.images.isEmpty()) {
            JsonPrimitive(message.content)
        } else {
            buildJsonArray {
                if (message.content.isNotBlank()) {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", message.content)
                        },
                    )
                }
                message.images.forEach { uri ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", uri) }
                        },
                    )
                }
            }
        }

    companion object {
        const val DEFAULT_TITLE = "Untitled conversation"
    }
}

/**
 * When tools are enabled, prepend the current date and time so the model can
 * interpret relative wording ("today", "latest", "last week") — essential for
 * answering things like "who won the last F1 race" after a web search.
 */
internal fun buildSystemPrompt(systemPrompt: String, toolsEnabled: Boolean): String = buildString {
    if (toolsEnabled) {
        append("Current date and time: ")
            .append(ZonedDateTime.now().format(dateTimeFormat))
            .append(". Use this to interpret relative dates such as \"today\", \"latest\" or \"last week\".\n\n")
        append("When a tool is needed, call it directly and keep going; do not narrate between tool calls. ")
            .append("Only write your final answer once you have everything you need.\n\n")
    }
    append(systemPrompt)
}.trim()
