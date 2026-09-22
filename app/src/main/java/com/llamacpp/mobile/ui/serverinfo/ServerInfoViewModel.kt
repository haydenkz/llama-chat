package com.llamacpp.mobile.ui.serverinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ServerProps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class ServerInfoUiState(
    val server: ServerConfig? = null,
    val props: ServerProps? = null,
    val models: List<LlamaModel> = emptyList(),
    val selectedModel: String? = null,
    val slotsJson: String? = null,
    val slotsError: String? = null,
    val metricsJson: String? = null,
    val metricsError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class ServerInfoViewModel(
    private val api: LlamaApi,
    private val serverRepository: ServerRepository,
) : ViewModel() {

    private val prettyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(ServerInfoUiState())
    val state: StateFlow<ServerInfoUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            serverRepository.activeServer.collect { server ->
                _state.update { it.copy(server = server) }
                refresh()
            }
        }
    }

    fun selectModel(model: String) {
        _state.update { it.copy(selectedModel = model, slotsJson = null, metricsJson = null, metricsError = null) }
        loadRuntime(model)
    }

    fun refresh() {
        val server = _state.value.server ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Each call is independent so one failure can't blank the whole screen.
            val props = runCatching { api.props(server) }.getOrNull()
            val models = runCatching { api.models(server) }.getOrDefault(emptyList())
            val selected = _state.value.selectedModel
                ?: models.firstOrNull { it.isLoaded }?.id
                ?: models.firstOrNull()?.id
            _state.update {
                it.copy(props = props, models = models, selectedModel = selected, loading = false)
            }
            selected?.let { loadRuntime(it) }
        }
    }

    private fun loadRuntime(model: String) {
        val server = _state.value.server ?: return
        viewModelScope.launch {
            val slots = runCatching { api.slots(server, model) }
            _state.update {
                it.copy(
                    slotsJson = slots.getOrNull()?.let(::pretty),
                    slotsError = slots.exceptionOrNull()?.message,
                )
            }

            runCatching { api.rawJson(server, "/metrics", model) }
                .onSuccess { element ->
                    _state.update { it.copy(metricsJson = pretty(element), metricsError = null) }
                }
                .onFailure { t ->
                    _state.update { it.copy(metricsJson = null, metricsError = t.message) }
                }
        }
    }

    private fun pretty(raw: String): String =
        runCatching {
            prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(raw))
        }.getOrDefault(raw)

    private fun pretty(element: JsonElement): String =
        runCatching { prettyJson.encodeToString(JsonElement.serializer(), element) }
            .getOrDefault(element.toString())
}
