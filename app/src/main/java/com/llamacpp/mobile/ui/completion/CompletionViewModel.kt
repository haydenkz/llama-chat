package com.llamacpp.mobile.ui.completion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.CompletionStreamEvent
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.remote.dto.CompletionRequestDto
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompletionUiState(
    val server: ServerConfig? = null,
    val models: List<LlamaModel> = emptyList(),
    val selectedModel: String? = null,
    val prompt: String = "",
    val output: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val temperature: Float = 0.6f,
    val maxTokens: Int = 512,
)

class CompletionViewModel(
    private val api: LlamaApi,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CompletionUiState())
    val state: StateFlow<CompletionUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            serverRepository.activeServer.collect { server ->
                _state.update { it.copy(server = server) }
                runCatching { api.models(server) }.onSuccess { list ->
                    _state.update { current ->
                        current.copy(
                            models = list,
                            selectedModel = current.selectedModel
                                ?: list.firstOrNull { model -> model.isLoaded }?.id
                                ?: list.firstOrNull()?.id,
                        )
                    }
                }
            }
        }
    }

    fun onPrompt(value: String) = _state.update { it.copy(prompt = value) }
    fun onModel(value: String) = _state.update { it.copy(selectedModel = value) }
    fun onTemperature(value: Float) = _state.update { it.copy(temperature = value) }
    fun onMaxTokens(value: Int) = _state.update { it.copy(maxTokens = value) }

    fun run() {
        if (_state.value.isStreaming) return
        val server = _state.value.server ?: return
        val prompt = _state.value.prompt
        if (prompt.isBlank()) return

        job = viewModelScope.launch {
            _state.update { it.copy(isStreaming = true, output = "", error = null) }
            val builder = StringBuilder()
            try {
                api.streamCompletion(
                    server,
                    CompletionRequestDto(
                        model = _state.value.selectedModel,
                        prompt = prompt,
                        stream = true,
                        temperature = _state.value.temperature,
                        maxTokens = _state.value.maxTokens,
                    ),
                ).collect { event ->
                    when (event) {
                        is CompletionStreamEvent.Delta -> {
                            builder.append(event.text)
                            _state.update { it.copy(output = builder.toString()) }
                        }
                        is CompletionStreamEvent.Failed ->
                            _state.update { it.copy(error = event.message) }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update { it.copy(error = t.message) }
            } finally {
                _state.update { it.copy(isStreaming = false) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.update { it.copy(isStreaming = false) }
    }
}
