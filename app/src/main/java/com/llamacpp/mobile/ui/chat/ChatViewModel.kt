package com.llamacpp.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.ChatStreamEvent
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.remote.dto.ChatCompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.ChatMessageDto
import com.llamacpp.mobile.data.remote.dto.TimingsDto
import com.llamacpp.mobile.data.remote.dto.UsageDto
import com.llamacpp.mobile.data.repo.ChatRepository
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.ToolRegistry
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.Conversation
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.SamplerSettings
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ToolCall
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

data class ChatUiState(
    val server: ServerConfig? = null,
    val serverOnline: Boolean? = null,
    val models: List<LlamaModel> = emptyList(),
    val loadingModels: Boolean = false,
    /** Models with an in-flight load/unload request. */
    val pendingModelIds: Set<String> = emptySet(),
    val conversations: List<Conversation> = emptyList(),
    val selectedId: String? = null,
    /** Model chosen before/independently of a conversation. */
    val selectedModelId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val settings: SamplerSettings = SamplerSettings(),
    /** Tool names the user has switched off in Settings → Tools. */
    val disabledTools: Set<String> = emptySet(),
    val isStreaming: Boolean = false,
    val streamContent: String = "",
    val streamReasoning: String = "",
    /** Transient status shown while a tool call runs, e.g. "Searching the web…". */
    val toolActivity: String? = null,
    val error: String? = null,
    val tokensPerSecond: Double? = null,
    val statusMessage: String? = null,
) {
    val conversation: Conversation? get() = conversations.firstOrNull { it.id == selectedId }
    /**
     * The model in effect. The user's explicit selection wins over the
     * conversation's stored model so switching models updates the UI immediately
     * instead of waiting for the DB round-trip (which showed a stale model).
     */
    val activeModelId: String? get() = selectedModelId ?: conversation?.model
    val selectedModel: LlamaModel? get() = models.firstOrNull { it.id == activeModelId }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val api: LlamaApi,
    private val chatRepository: ChatRepository,
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val toolRegistry: ToolRegistry,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var completionId: String? = null

    init {
        viewModelScope.launch {
            serverRepository.activeServer.distinctUntilChanged().collect { server ->
                _state.update { it.copy(server = server) }
                refreshModels()
                refreshHealth()
            }
        }
        viewModelScope.launch {
            serverRepository.activeServer.distinctUntilChanged()
                .flatMapLatest { chatRepository.conversations(it.id) }
                .collect { list -> _state.update { it.copy(conversations = list) } }
        }
        viewModelScope.launch {
            _state.map { it.selectedId }.distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else chatRepository.messages(id)
                }
                .collect { messages -> _state.update { it.copy(messages = messages) } }
        }
        viewModelScope.launch {
            _state.map { it.activeModelId }.distinctUntilChanged()
                .flatMapLatest { model -> settingsRepository.sampler(model) }
                .collect { settings -> _state.update { it.copy(settings = settings) } }
        }
        viewModelScope.launch {
            settingsRepository.disabledTools.collect { disabled ->
                _state.update { it.copy(disabledTools = disabled) }
            }
        }
    }

    // ---- data refresh -----------------------------------------------------

    fun refreshHealth() {
        val server = _state.value.server ?: return
        viewModelScope.launch {
            val health = api.health(server)
            _state.update { it.copy(serverOnline = health.ok) }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _state.update { it.copy(loadingModels = true) }
            val list = fetchModels()
            if (list != null) applyModels(list) else _state.update { it.copy(loadingModels = false) }
        }
    }

    private suspend fun fetchModels(): List<LlamaModel>? {
        val server = _state.value.server ?: return null
        return runCatching { api.models(server) }.getOrNull()
    }

    private fun applyModels(list: List<LlamaModel>) {
        _state.update { current ->
            // Default to the loaded model (or the first available) so the app is
            // usable immediately; the header remains the selector.
            val fallback = current.activeModelId
                ?: list.firstOrNull { it.isLoaded }?.id
                ?: list.firstOrNull()?.id
            current.copy(
                models = list,
                loadingModels = false,
                selectedModelId = current.selectedModelId ?: fallback,
            )
        }
    }

    fun loadModel(id: String) = changeModelLoadState(id)

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    private fun changeModelLoadState(id: String) {
        val server = _state.value.server ?: return
        if (id in _state.value.pendingModelIds) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    pendingModelIds = it.pendingModelIds + id,
                    statusMessage = "Loading ${shortName(id)}…",
                )
            }

            val failure = runCatching { api.loadModel(server, id) }.exceptionOrNull()
            if (failure != null) {
                _state.update {
                    it.copy(
                        pendingModelIds = it.pendingModelIds - id,
                        statusMessage = "Load failed: ${failure.message}",
                    )
                }
                return@launch
            }
            waitForModelState(id)
        }
    }

    /** Polls until the server reports the model loaded (or gives up). */
    private suspend fun waitForModelState(id: String) {
        repeat(90) {
            val list = fetchModels()
            if (list != null) applyModels(list)
            if (_state.value.models.firstOrNull { it.id == id }?.isLoaded == true) {
                _state.update { it.copy(pendingModelIds = it.pendingModelIds - id, statusMessage = null) }
                return
            }
            delay(1000)
        }
        _state.update { it.copy(pendingModelIds = it.pendingModelIds - id, statusMessage = null) }
    }

    private fun shortName(id: String): String = id.substringAfterLast('/').take(28)

    // ---- conversations ----------------------------------------------------

    fun newConversation() {
        viewModelScope.launch {
            val serverId = _state.value.server?.id ?: return@launch
            val conversation = chatRepository.createConversation(
                serverId = serverId,
                model = _state.value.activeModelId ?: defaultModelId(),
                systemPrompt = _state.value.settings.systemPrompt,
            )
            _state.update { it.copy(selectedId = conversation.id, error = null) }
        }
    }

    fun selectConversation(id: String) {
        val model = _state.value.conversations.firstOrNull { it.id == id }?.model
        _state.update {
            it.copy(
                selectedId = id,
                selectedModelId = model ?: it.selectedModelId,
                error = null,
            )
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_state.value.selectedId == id) _state.update { it.copy(selectedId = null) }
        }
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { chatRepository.renameConversation(id, title) }
    }

    /** Works with or without an existing conversation. Selecting loads if needed. */
    fun selectModel(modelId: String) {
        _state.update { it.copy(selectedModelId = modelId) }
        val conversation = _state.value.conversation
        if (conversation != null) {
            viewModelScope.launch { chatRepository.setConversationModel(conversation.id, modelId) }
        }
        val model = _state.value.models.firstOrNull { it.id == modelId }
        if (model != null && !model.isLoaded && !model.isLoading) {
            // Auto-load the chosen model; the router evicts others as needed.
            loadModel(modelId)
        } else {
            refreshModels()
        }
    }

    fun updateSettings(settings: SamplerSettings) {
        _state.update { it.copy(settings = settings) }
        viewModelScope.launch {
            settingsRepository.saveSampler(_state.value.activeModelId, settings)
        }
    }

    fun setSystemPrompt(prompt: String) {
        updateSettings(_state.value.settings.copy(systemPrompt = prompt))
        _state.value.conversation?.let { conversation ->
            viewModelScope.launch { chatRepository.setConversationSystemPrompt(conversation.id, prompt) }
        }
    }

    // ---- generation -------------------------------------------------------

    fun send(text: String, images: List<String> = emptyList()) {
        if (text.isBlank() && images.isEmpty()) return
        if (_state.value.isStreaming) return

        streamJob = viewModelScope.launch {
            var conversation = _state.value.conversation
            if (conversation == null) {
                val serverId = _state.value.server?.id ?: return@launch
                conversation = chatRepository.createConversation(
                    serverId = serverId,
                    model = _state.value.activeModelId ?: defaultModelId(),
                    systemPrompt = _state.value.settings.systemPrompt,
                )
                _state.update { it.copy(selectedId = conversation.id) }
            }
            if (_state.value.activeModelId.isNullOrBlank()) {
                _state.update { it.copy(error = "Select a model before sending.") }
                return@launch
            }
            chatRepository.insertMessage(
                ChatMessage(
                    conversationId = conversation.id,
                    role = ChatRole.User,
                    content = text,
                    images = images,
                ),
            )
            chatRepository.autoTitleFromFirstMessage(conversation.id, text)
            runCompletion(conversation.id)
        }
    }

    fun stop() {
        streamJob?.cancel()
        val server = _state.value.server
        val model = _state.value.activeModelId
        val id = completionId
        if (server != null && model != null && id != null) {
            viewModelScope.launch { api.abort(server, model, id) }
        }
    }

    fun regenerate() {
        if (_state.value.isStreaming) return
        val conversation = _state.value.conversation ?: return
        streamJob = viewModelScope.launch {
            val lastAssistant = _state.value.messages.lastOrNull { it.role == ChatRole.Assistant }
                ?: return@launch
            chatRepository.deleteMessagesFrom(conversation.id, lastAssistant.id)
            runCompletion(conversation.id)
        }
    }

    fun editMessage(message: ChatMessage, newText: String) {
        if (_state.value.isStreaming) return
        streamJob = viewModelScope.launch {
            chatRepository.deleteMessagesFrom(message.conversationId, message.id)
            chatRepository.insertMessage(
                message.copy(id = 0L, content = newText, error = null, createdAt = System.currentTimeMillis()),
            )
            runCompletion(message.conversationId)
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch { chatRepository.deleteMessage(message.id) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // ---- internals --------------------------------------------------------

    private suspend fun runCompletion(conversationId: String) {
        val snapshot = _state.value
        val server = snapshot.server ?: return
        val conversation = snapshot.conversation ?: return
        // Use the live selection, not the conversation record, so switching models
        // takes effect immediately (the DB write may not have landed yet).
        val model = snapshot.activeModelId ?: conversation.model ?: return
        val enabledTools = toolRegistry.all()
            .map { it.name }
            .filterNot { it in snapshot.disabledTools }
            .toSet()
        val tools = toolRegistry.definitions(enabledTools).takeIf { it.isNotEmpty() }

        _state.update {
            it.copy(
                isStreaming = true,
                streamContent = "",
                streamReasoning = "",
                toolActivity = null,
                error = null,
                tokensPerSecond = null,
            )
        }
        completionId = null

        try {
            var iterations = 0
            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++
                val history = chatRepository.messages(conversationId).first()
                if (history.isEmpty()) break
                val request = chatRepository.buildRequest(
                    model = model,
                    systemPrompt = conversation.systemPrompt.ifBlank { snapshot.settings.systemPrompt },
                    history = history,
                    settings = snapshot.settings,
                    tools = tools,
                )
                val outcome = streamOnce(server, request, model, conversationId)
                if (outcome.failure != null || outcome.toolCalls.isEmpty()) break

                // Run each requested tool, feed the results back, and continue.
                outcome.toolCalls.forEach { call ->
                    _state.update { it.copy(toolActivity = toolActivityLabel(call)) }
                    val output = executeTool(call)
                    chatRepository.insertMessage(
                        ChatMessage(
                            conversationId = conversationId,
                            role = ChatRole.Tool,
                            content = output,
                            toolCallId = call.id,
                            toolName = call.name,
                        ),
                    )
                }
                _state.update { it.copy(toolActivity = null, streamContent = "", streamReasoning = "") }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            _state.update { it.copy(error = t.message) }
        } finally {
            withContext(NonCancellable) {
                _state.update {
                    it.copy(
                        isStreaming = false,
                        streamContent = "",
                        streamReasoning = "",
                        toolActivity = null,
                    )
                }
                completionId = null
            }
        }

        // Name the conversation with the model after the first completed answer.
        viewModelScope.launch { maybeGenerateTitle(conversationId, server, model) }
    }

    /**
     * Asks the model for a short, specific title for the conversation. Runs only
     * once, after the first assistant answer, and replaces the provisional title.
     */
    private suspend fun maybeGenerateTitle(conversationId: String, server: ServerConfig, model: String) {
        val messages = chatRepository.messages(conversationId).first()
        val answered = messages.count { it.role == ChatRole.Assistant && it.content.isNotBlank() }
        if (answered != 1) return

        val user = messages.firstOrNull { it.role == ChatRole.User }?.content?.take(600) ?: return
        val assistant = messages.firstOrNull { it.role == ChatRole.Assistant && it.content.isNotBlank() }
            ?.content?.take(600).orEmpty()

        val prompt = buildString {
            append("Write a short, specific title (3 to 6 words) for the conversation below. ")
            append("Reply with only the title — no quotes, no trailing punctuation.\n\n")
            append("User: ").append(user).append('\n')
            if (assistant.isNotBlank()) append("Assistant: ").append(assistant).append('\n')
        }

        val title = runCatching {
            api.chatCompletion(
                server,
                ChatCompletionRequestDto(
                    model = model,
                    messages = listOf(ChatMessageDto(ChatRole.User.wire, JsonPrimitive(prompt))),
                    stream = false,
                    temperature = 0.3f,
                    maxTokens = 32,
                    cachePrompt = false,
                    reasoning = false,
                ),
            )
        }.getOrNull()
            ?.choices?.firstOrNull()?.message?.content
            ?.let(::sanitizeTitle)

        if (!title.isNullOrBlank()) chatRepository.renameConversation(conversationId, title)
    }

    private fun sanitizeTitle(raw: String): String =
        raw.lineSequence().firstOrNull().orEmpty()
            .trim()
            .trim('"', '\'', '“', '”', '`', '*', '#', '.', ':', ' ')
            .take(60)
            .trim()

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private class StreamOutcome(
        val toolCalls: List<ToolCall>,
        val failure: String?,
    )

    /**
     * One streaming pass. Persists the assistant message (including any tool
     * calls) and returns what the model asked for, without touching `isStreaming`.
     */
    private suspend fun streamOnce(
        server: ServerConfig,
        request: ChatCompletionRequestDto,
        model: String,
        conversationId: String,
    ): StreamOutcome {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolAcc = linkedMapOf<Int, ToolCallAccumulator>()
        var timings: TimingsDto? = null
        var usage: UsageDto? = null
        var failure: String? = null
        var lastEmit = 0L

        try {
            api.streamChat(server, request).collect { event ->
                when (event) {
                    is ChatStreamEvent.Chunk -> {
                        event.chunk.id?.let { completionId = it }
                        event.chunk.choices.firstOrNull()?.delta?.let { delta ->
                            delta.content?.let { content.append(it) }
                            delta.reasoningContent?.let { reasoning.append(it) }
                            delta.toolCalls?.forEach { tc ->
                                val index = tc.index ?: 0
                                val acc = toolAcc.getOrPut(index) { ToolCallAccumulator() }
                                if (!tc.id.isNullOrBlank()) acc.id = tc.id
                                tc.function?.name?.takeIf { it.isNotBlank() }?.let { acc.name = it }
                                tc.function?.arguments?.let { acc.arguments.append(it) }
                            }
                        }
                        event.chunk.timings?.let { timings = it }
                        event.chunk.usage?.let { usage = it }

                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 30) {
                            lastEmit = now
                            _state.update {
                                it.copy(
                                    streamContent = content.toString(),
                                    streamReasoning = reasoning.toString(),
                                )
                            }
                        }
                    }
                    is ChatStreamEvent.Failed -> failure = event.message
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            failure = t.message
        }

        val text = content.toString()
        val think = reasoning.toString()
        val toolCalls = toolAcc.values.mapNotNull { acc ->
            if (acc.name.isBlank()) {
                null
            } else {
                ToolCall(
                    id = acc.id.ifBlank { UUID.randomUUID().toString() },
                    name = acc.name,
                    arguments = acc.arguments.toString(),
                )
            }
        }
        val speed = timings?.predictedPerSecond
        val assistant = if (text.isNotBlank() || think.isNotBlank() || toolCalls.isNotEmpty() || failure != null) {
            ChatMessage(
                conversationId = conversationId,
                role = ChatRole.Assistant,
                content = text,
                reasoning = think,
                model = model,
                tokenCount = timings?.predictedN ?: usage?.completionTokens,
                durationMs = timings?.predictedMs,
                promptTokenCount = usage?.promptTokens ?: timings?.promptN,
                tokensPerSecond = speed,
                toolCalls = toolCalls,
                error = failure,
            )
        } else {
            null
        }

        val rowId = assistant?.let { chatRepository.insertMessage(it) }
        _state.update { current ->
            val messages = if (assistant != null && rowId != null && current.messages.none { it.id == rowId }) {
                current.messages + assistant.copy(id = rowId)
            } else {
                current.messages
            }
            current.copy(messages = messages, error = failure, tokensPerSecond = speed)
        }
        return StreamOutcome(toolCalls, failure)
    }

    private fun toolActivityLabel(call: ToolCall): String = when (call.name) {
        WebSearchTool.NAME -> "Searching the web for \"${queryOf(call).ifBlank { "…" }}\"…"
        else -> "Using ${toolRegistry.byName(call.name)?.displayName ?: call.name}…"
    }

    private suspend fun executeTool(call: ToolCall): String = toolRegistry.execute(call)

    private fun queryOf(call: ToolCall): String =
        runCatching {
            json.parseToJsonElement(call.arguments).jsonObject["query"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty()

    private fun defaultModelId(): String? =
        _state.value.models.firstOrNull { it.isLoaded }?.id
            ?: _state.value.models.firstOrNull()?.id

    companion object {
        private const val MAX_TOOL_ITERATIONS = 4
    }
}
