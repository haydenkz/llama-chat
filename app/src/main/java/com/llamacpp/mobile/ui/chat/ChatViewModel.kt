package com.llamacpp.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.ChatStreamEvent
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.remote.dto.ChatCompletionRequestDto
import com.llamacpp.mobile.data.remote.dto.ChatMessageDto
import com.llamacpp.mobile.data.remote.dto.MessageDto
import com.llamacpp.mobile.data.remote.dto.TimingsDto
import com.llamacpp.mobile.data.remote.dto.UsageDto
import com.llamacpp.mobile.data.repo.ChatRepository
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.ToolCallText
import com.llamacpp.mobile.data.tools.ToolRegistry
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.Conversation
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.SamplerSettings
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ToolCall
import com.llamacpp.mobile.domain.model.samplerSettingsFromParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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
    /** Why the last model refresh failed, or null. */
    val modelsError: String? = null,
    /** Models with an in-flight load/unload request. */
    val pendingModelIds: Set<String> = emptySet(),
    val conversations: List<Conversation> = emptyList(),
    val selectedId: String? = null,
    /** Model chosen before/independently of a conversation. */
    val selectedModelId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val settings: SamplerSettings = SamplerSettings(),
    /** True when the user has tuned the sampler for this model. */
    val settingsCustomized: Boolean = false,
    /** Tool names the user has switched off in Settings → Tools. */
    val disabledTools: Set<String> = emptySet(),
    val isStreaming: Boolean = false,
    /** The conversation currently being generated (may differ from the selected one). */
    val streamingConversationId: String? = null,
    val streamContent: String = "",
    val streamReasoning: String = "",
    /** Thinking time already accumulated in the current turn (all passes so far). */
    val streamThinkingMs: Long = 0L,
    /** When the current thinking span began, or null while answer text is being written. */
    val streamThinkingSince: Long? = null,
    /** Tool calls the model is still writing in the current pass (arguments may be partial). */
    val streamToolCalls: List<ToolCall> = emptyList(),
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
    private var titleJob: Job? = null
    private var settingsPersistJob: Job? = null
    private var completionId: String? = null

    init {
        // DataStore/Room flows can throw (corrupt file, I/O); a failure must degrade,
        // never crash the process from an unguarded collector.
        viewModelScope.launch {
            serverRepository.activeServer.distinctUntilChanged()
                .catch { emit(_state.value.server ?: ServerRepository.defaultServer()) }
                .collect { server ->
                    _state.update { it.copy(server = server) }
                    refreshModels()
                    refreshHealth()
                }
        }
        viewModelScope.launch {
            serverRepository.activeServer.distinctUntilChanged()
                .flatMapLatest { chatRepository.conversations(it.id).catch { emit(emptyList()) } }
                .catch { emit(_state.value.conversations) }
                .collect { list -> _state.update { it.copy(conversations = list) } }
        }
        viewModelScope.launch {
            _state.map { it.selectedId }.distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList()) else chatRepository.messages(id).catch { emit(emptyList()) }
                }
                .collect { messages -> _state.update { it.copy(messages = messages) } }
        }
        viewModelScope.launch {
            combine(
                _state.map { it.activeModelId }.distinctUntilChanged(),
                serverRepository.activeServer.distinctUntilChanged()
                    .catch { emit(_state.value.server ?: ServerRepository.defaultServer()) },
            ) { modelId, server -> modelId to server }
                .collect { (modelId, server) ->
                    // Use the user's saved sampler if any; otherwise seed from the
                    // model's own defaults reported by /props?model=…, so switching
                    // models shows that model's real temperature/top-k/min-p/etc.
                    val saved = modelId?.let { id ->
                        runCatching { settingsRepository.savedSampler(id).first() }.getOrNull()
                    }
                    if (saved != null) {
                        _state.update { it.copy(settings = saved, settingsCustomized = true) }
                    } else {
                        val defaults = modelId?.let { id ->
                            runCatching { api.props(server, id) }.getOrNull()?.samplingDefaults
                        }
                        _state.update {
                            it.copy(
                                settings = defaults?.let(::samplerSettingsFromParams) ?: SamplerSettings(),
                                settingsCustomized = false,
                            )
                        }
                    }
                }
        }
        viewModelScope.launch {
            settingsRepository.disabledTools
                .catch { emit(_state.value.disabledTools) }
                .collect { disabled -> _state.update { it.copy(disabledTools = disabled) } }
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
            val result = fetchModels()
            result.onSuccess(::applyModels).onFailure { failure ->
                _state.update { it.copy(loadingModels = false, modelsError = failure.message ?: "Could not load models") }
            }
        }
    }

    private suspend fun fetchModels(): Result<List<LlamaModel>> {
        val server = _state.value.server ?: return Result.failure(IllegalStateException("No server selected"))
        return runCatching { api.models(server) }
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
                modelsError = null,
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
            fetchModels().getOrNull()?.let(::applyModels)
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
        // Stop a running generation first so no rows land in a deleted conversation.
        if (_state.value.streamingConversationId == id) stop()
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
        _state.update { it.copy(settings = settings, settingsCustomized = true) }
        // Debounced: sliders and text fields call this per frame/keystroke.
        settingsPersistJob?.cancel()
        settingsPersistJob = viewModelScope.launch {
            delay(SETTINGS_PERSIST_DEBOUNCE_MS)
            settingsRepository.saveSampler(_state.value.activeModelId, settings)
        }
    }

    /** Drops the saved sampler for the active model and returns to its defaults. */
    fun resetSettings() {
        viewModelScope.launch {
            val modelId = _state.value.activeModelId
            settingsRepository.clearSampler(modelId)
            val server = _state.value.server
            val defaults = if (modelId != null && server != null) {
                runCatching { api.props(server, modelId) }.getOrNull()?.samplingDefaults
            } else {
                null
            }
            _state.update {
                it.copy(
                    settings = defaults?.let(::samplerSettingsFromParams) ?: SamplerSettings(),
                    settingsCustomized = false,
                )
            }
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
        launchGeneration(_state.value.selectedId) {
            var conversation = _state.value.conversation
            if (conversation == null) {
                val serverId = _state.value.server?.id ?: return@launchGeneration null
                conversation = chatRepository.createConversation(
                    serverId = serverId,
                    model = _state.value.activeModelId ?: defaultModelId(),
                    systemPrompt = _state.value.settings.systemPrompt,
                )
                _state.update { it.copy(selectedId = conversation.id, streamingConversationId = conversation.id) }
            }
            if (_state.value.activeModelId.isNullOrBlank()) {
                _state.update { it.copy(error = "Select a model before sending.") }
                return@launchGeneration null
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
            conversation.id
        }
    }

    fun stop() {
        // Snapshot the id first: cancelling the job clears it.
        val id = completionId
        streamJob?.cancel()
        val server = _state.value.server
        val model = _state.value.activeModelId
        if (server != null && model != null && id != null) {
            viewModelScope.launch { runCatching { api.abort(server, model, id) } }
        }
    }

    fun regenerate() {
        launchGeneration(_state.value.selectedId) {
            val conversation = _state.value.conversation ?: return@launchGeneration null
            val lastAssistant = _state.value.messages.lastOrNull { it.role == ChatRole.Assistant }
                ?: return@launchGeneration null
            chatRepository.deleteMessagesFrom(conversation.id, lastAssistant.id)
            conversation.id
        }
    }

    fun editMessage(message: ChatMessage, newText: String) {
        launchGeneration(message.conversationId) {
            chatRepository.deleteMessagesFrom(message.conversationId, message.id)
            chatRepository.insertMessage(
                message.copy(id = 0L, content = newText, error = null, createdAt = System.currentTimeMillis()),
            )
            message.conversationId
        }
    }

    /** Re-runs the last turn after a failure, dropping a trailing failed assistant row if one was kept. */
    fun retry() {
        launchGeneration(_state.value.selectedId) {
            val conversation = _state.value.conversation ?: return@launchGeneration null
            val last = _state.value.messages.lastOrNull()
            if (last != null && last.role == ChatRole.Assistant && last.error != null) {
                chatRepository.deleteMessage(last.id)
            }
            conversation.id
        }
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch { chatRepository.deleteMessage(message.id) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    // ---- internals --------------------------------------------------------

    /** State writes that belong to one conversation must not land while another is selected. */
    private inline fun updateIfSelected(conversationId: String, block: (ChatUiState) -> ChatUiState) {
        _state.update { current -> if (current.selectedId == conversationId) block(current) else current }
    }

    private fun beginStreaming(conversationId: String?) {
        _state.update { it.copy(isStreaming = true, streamingConversationId = conversationId, error = null) }
    }

    private fun clearStreaming() {
        _state.update {
            it.copy(
                isStreaming = false,
                streamingConversationId = null,
                streamContent = "",
                streamReasoning = "",
                streamThinkingMs = 0L,
                streamThinkingSince = null,
                streamToolCalls = emptyList(),
            )
        }
    }

    /**
     * Single entry point for every generation. Marks streaming synchronously (so a
     * double tap cannot start two generations while `prepare` is still suspended),
     * then runs `prepare` to resolve the conversation and `runCompletion` on it.
     * `conversationId` is the conversation the generation is expected to land in,
     * if known up front; returning null from `prepare` aborts without generating.
     */
    private fun launchGeneration(conversationId: String?, prepare: suspend () -> String?) {
        if (_state.value.isStreaming) return
        titleJob?.cancel()
        beginStreaming(conversationId)
        streamJob = viewModelScope.launch {
            try {
                val conversationId = prepare() ?: return@launch
                runCompletion(conversationId)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(error = t.message) }
            } finally {
                clearStreaming()
            }
        }
    }

    private suspend fun runCompletion(conversationId: String) {
        val snapshot = _state.value
        val server = snapshot.server ?: return
        val conversation = chatRepository.conversation(conversationId) ?: return
        // Use the live selection, not the conversation record, so switching models
        // takes effect immediately (the DB write may not have landed yet).
        val model = snapshot.activeModelId ?: conversation.model ?: return
        val enabledTools = toolRegistry.all()
            .map { it.name }
            .filterNot { it in snapshot.disabledTools }
            .toSet()
        val tools = toolRegistry.definitions(enabledTools).takeIf { it.isNotEmpty() }
        val systemPrompt = conversation.systemPrompt.ifBlank { snapshot.settings.systemPrompt }

        _state.update {
            it.copy(
                isStreaming = true,
                streamingConversationId = conversationId,
                streamContent = "",
                streamReasoning = "",
                streamThinkingMs = 0L,
                streamThinkingSince = null,
                streamToolCalls = emptyList(),
            )
        }
        updateIfSelected(conversationId) { it.copy(error = null, tokensPerSecond = null) }
        completionId = null

        // Thinking time is everything between the request and the first answer
        // token, including tool execution; each pass measures from this cursor.
        var thinkingCursor = System.currentTimeMillis()
        try {
            var iterations = 0
            var lastOutcome: StreamOutcome? = null
            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++
                val history = chatRepository.messages(conversationId).first()
                if (history.isEmpty()) break
                val request = chatRepository.buildRequest(model, systemPrompt, history, snapshot.settings, tools)
                // After a tool call, the next pass usually narrates before calling again.
                val outcome = streamOnce(server, request, model, conversationId, thinkingCursor, afterTools = iterations > 1)
                lastOutcome = outcome
                thinkingCursor = outcome.endedAt
                if (outcome.failure != null || outcome.toolCalls.isEmpty()) break

                // Run each requested tool, feed the results back, and continue.
                outcome.toolCalls.forEach { call ->
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
            }
            // The cap was hit with tool results still unanswered: one last pass
            // without tools so the turn ends in an answer. Tool calls the model
            // still emits as text cannot run here, so they are dropped.
            if (lastOutcome?.failure == null && lastOutcome?.toolCalls?.isNotEmpty() == true) {
                val history = chatRepository.messages(conversationId).first()
                if (history.isNotEmpty()) {
                    val request = chatRepository.buildRequest(model, systemPrompt, history, snapshot.settings, tools = null)
                    val outcome = streamOnce(server, request, model, conversationId, thinkingCursor, afterTools = true, acceptTextToolCalls = false)
                    if (outcome.failure == null && outcome.answered.not()) {
                        updateIfSelected(conversationId) {
                            it.copy(error = "Stopped after $MAX_TOOL_ITERATIONS tool calls without a final answer.")
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            updateIfSelected(conversationId) { it.copy(error = t.message) }
        } finally {
            withContext(NonCancellable) {
                clearStreaming()
                completionId = null
            }
        }

        // Name the conversation with the model after the first completed answer.
        titleJob = viewModelScope.launch { maybeGenerateTitle(conversationId, server, model) }
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
                    maxTokens = 400,
                    cachePrompt = false,
                    reasoning = false,
                ),
            )
        }.getOrNull()
            ?.choices?.firstOrNull()?.message
            ?.let(::utilityText)
            ?.let(::sanitizeTitle)

        if (!title.isNullOrBlank()) chatRepository.renameConversation(conversationId, title)
    }

    private fun sanitizeTitle(raw: String): String =
        raw.lineSequence().firstOrNull().orEmpty()
            .trim()
            .trim('"', '\'', '“', '”', '`', '*', '#', '.', ':', ' ')
            .take(60)
            .trim()

    /**
     * Text from the title response. Reasoning models served with
     * `--reasoning` may put everything in `reasoning_content`; take its last line
     * as the answer in that case.
     */
    private fun utilityText(message: MessageDto): String? {
        message.content?.takeIf { it.isNotBlank() }?.let { return it }
        return message.reasoningContent
            ?.lineSequence()
            ?.map { it.trim() }
            ?.lastOrNull { it.isNotBlank() }
    }

    private class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private class StreamOutcome(
        val toolCalls: List<ToolCall>,
        val failure: String?,
        /** True when the pass produced visible answer text. */
        val answered: Boolean,
        /** When this pass ended; the next pass measures thinking from here. */
        val endedAt: Long,
    )

    /**
     * One streaming pass. Persists the assistant message (including any tool
     * calls) and returns what the model asked for, without touching `isStreaming`.
     * Cancellation (Stop) persists whatever was generated before rethrowing.
     * `thinkingSince` is the instant the current thinking span started.
     * `acceptTextToolCalls` controls whether `<tool_call>` markup found in the
     * text becomes real calls (false on the final, tool-less pass).
     */
    private suspend fun streamOnce(
        server: ServerConfig,
        request: ChatCompletionRequestDto,
        model: String,
        conversationId: String,
        thinkingSince: Long,
        afterTools: Boolean = false,
        acceptTextToolCalls: Boolean = true,
    ): StreamOutcome {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolAcc = linkedMapOf<Int, ToolCallAccumulator>()
        var timings: TimingsDto? = null
        var usage: UsageDto? = null
        var failure: String? = null
        var cancelled: CancellationException? = null
        var lastEmit = 0L
        var thinkingEndedAt: Long? = null
        // A stale id from a previous tool iteration must not be the one we abort.
        completionId = null
        _state.update { it.copy(streamThinkingSince = thinkingSince) }

        fun endThinking(now: Long) {
            if (thinkingEndedAt != null) return
            thinkingEndedAt = now
            _state.update {
                it.copy(streamThinkingMs = it.streamThinkingMs + (now - thinkingSince), streamThinkingSince = null)
            }
        }

        // Text that turned out to precede a tool call was narration, not the answer:
        // reopen the thinking span so the clock covers it.
        fun resumeThinking() {
            val ended = thinkingEndedAt ?: return
            thinkingEndedAt = null
            _state.update {
                it.copy(streamThinkingMs = it.streamThinkingMs - (ended - thinkingSince), streamThinkingSince = thinkingSince)
            }
        }

        // A first pass streams text straight into the answer area; if a tool call
        // follows, it was narration and moves under Thinking. After a tool call,
        // text streams live in the work area instead (narration is likely there)
        // and moves to the answer once it clearly is one: a line break or a
        // paragraph's worth of text.
        var promoted = !afterTools

        fun pendingToolCalls(): List<ToolCall> =
            toolAcc.entries.filter { it.value.name.isNotBlank() }.map { (index, acc) ->
                ToolCall(id = acc.id.ifBlank { "pending-$index" }, name = acc.name, arguments = acc.arguments.toString())
            }

        fun flush(visible: String, answering: Boolean) {
            val think = reasoning.toString()
            val tools = pendingToolCalls()
            _state.update {
                it.copy(
                    streamContent = if (answering) visible else "",
                    streamReasoning = if (answering) think else joinNonBlank(think, visible),
                    streamToolCalls = tools,
                )
            }
        }

        try {
            api.streamChat(server, request).collect { event ->
                when (event) {
                    is ChatStreamEvent.Chunk -> {
                        event.chunk.id?.let { completionId = it }
                        event.chunk.choices.firstOrNull()?.delta?.let { delta ->
                            delta.content?.let { content.append(it) }
                            delta.reasoningContent?.let { reasoning.append(it) }
                            delta.toolCalls?.forEach { tc ->
                                // Without an index, match by id; otherwise it is a new call.
                                val index = tc.index
                                    ?: toolAcc.entries.firstOrNull { tc.id != null && it.value.id == tc.id }?.key
                                    ?: toolAcc.size
                                val acc = toolAcc.getOrPut(index) { ToolCallAccumulator() }
                                if (!tc.id.isNullOrBlank()) acc.id = tc.id
                                tc.function?.name?.takeIf { it.isNotBlank() }?.let { acc.name = it }
                                tc.function?.arguments?.let { acc.arguments.append(it) }
                            }
                        }
                        event.chunk.timings?.let { timings = it }
                        event.chunk.usage?.let { usage = it }

                        val now = System.currentTimeMillis()
                        // Each emit reparses the whole markdown document in the UI, so
                        // back off as the answer grows.
                        val interval = when {
                            content.length < 4_000 -> 30L
                            content.length < 16_000 -> 90L
                            else -> 150L
                        }
                        if (now - lastEmit >= interval) {
                            lastEmit = now
                            val raw = content.toString()
                            // Tool-call markup arriving as text is not answer content.
                            val visible = ToolCallText.strip(raw)
                            val toolSignal = toolAcc.isNotEmpty() || ToolCallText.hasMarkup(raw)
                            if (!promoted && !toolSignal && looksLikeAnswer(visible)) promoted = true
                            val answering = promoted && !toolSignal && visible.isNotBlank()
                            if (answering) endThinking(now) else if (toolSignal) resumeThinking()
                            flush(visible, answering)
                        }
                    }
                    is ChatStreamEvent.Failed -> failure = event.message
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) cancelled = t else failure = t.message
        }
        val endedAt = System.currentTimeMillis()
        // Servers that miss the model's tool-call format hand it back as text.
        val extracted = ToolCallText.extract(content.toString())
        val toolPass = toolAcc.isNotEmpty() || (acceptTextToolCalls && extracted.toolCalls.isNotEmpty())
        if (toolPass) resumeThinking()
        endThinking(endedAt)

        val text = extracted.content
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
        }.ifEmpty { if (acceptTextToolCalls) extracted.toolCalls else emptyList() }
        val speed = timings?.predictedPerSecond
        // A pure failure produces no row: it surfaces through `error` instead.
        val assistant = if (text.isNotBlank() || think.isNotBlank() || toolCalls.isNotEmpty()) {
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
                thinkingMs = (thinkingEndedAt ?: endedAt) - thinkingSince,
                error = failure,
            )
        } else {
            null
        }

        val rowId = assistant?.let { withContext(NonCancellable) { chatRepository.insertMessage(it) } }
        // One atomic swap from the live buffers to the persisted row, so the pass is
        // never shown twice (or not at all) for a frame. A tool pass keeps the clock
        // running: tool execution counts as thinking.
        _state.update { current ->
            val cleared = current.copy(
                streamContent = "",
                streamReasoning = "",
                streamToolCalls = emptyList(),
                streamThinkingSince = if (toolPass && cancelled == null) endedAt else null,
            )
            if (current.selectedId != conversationId) return@update cleared
            val messages = if (assistant != null && rowId != null && current.messages.none { it.id == rowId }) {
                current.messages + assistant.copy(id = rowId)
            } else {
                current.messages
            }
            cleared.copy(
                messages = messages,
                // A persisted row shows its error inline; only a row-less failure needs the transient banner.
                error = failure.takeIf { assistant == null },
                tokensPerSecond = speed,
            )
        }
        cancelled?.let { throw it }
        return StreamOutcome(toolCalls, failure, answered = text.isNotBlank(), endedAt = endedAt)
    }

    private suspend fun executeTool(call: ToolCall): String = toolRegistry.execute(call)

    private fun joinNonBlank(vararg parts: String): String = parts.filter(String::isNotBlank).joinToString("\n\n")

    /** Updates between tool calls are single-line prose; an answer soon breaks a line or runs long. */
    private fun looksLikeAnswer(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.contains('\n') || trimmed.length >= ANSWER_PROMOTE_CHARS
    }


    private fun queryOf(call: ToolCall): String =
        runCatching {
            json.parseToJsonElement(call.arguments).jsonObject["query"]?.jsonPrimitive?.content
        }.getOrNull().orEmpty()

    private fun defaultModelId(): String? =
        _state.value.models.firstOrNull { it.isLoaded }?.id
            ?: _state.value.models.firstOrNull()?.id

    companion object {
        /** Passes with tools before forcing a tool-less answer; multi-step requests need several. */
        private const val MAX_TOOL_ITERATIONS = 12
        private const val SETTINGS_PERSIST_DEBOUNCE_MS = 400L
        /** Text after a tool call moves from the work area to the answer at this length. */
        private const val ANSWER_PROMOTE_CHARS = 400
    }
}
