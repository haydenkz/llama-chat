package com.llamacpp.mobile.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.baseUrlError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Steps of the first-run wizard. */
enum class OnboardingStep { Welcome, Server, Model }

class OnboardingViewModel(
    private val serverRepository: ServerRepository,
    private val settingsRepository: SettingsRepository,
    private val api: LlamaApi,
) : ViewModel() {

    var step by mutableStateOf(OnboardingStep.Welcome)
        private set

    var serverName by mutableStateOf("")
        private set
    var serverUrl by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var urlError by mutableStateOf<String?>(null)
        private set

    var testing by mutableStateOf(false)
        private set
    var testOk by mutableStateOf<Boolean?>(null)
        private set
    var testStatus by mutableStateOf<String?>(null)
        private set

    var models by mutableStateOf<List<LlamaModel>>(emptyList())
        private set
    var loadingModels by mutableStateOf(false)
        private set
    var selectedModel by mutableStateOf<String?>(null)
        private set
    var loadingModel by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val server = serverRepository.activeServer.first()
            serverName = server.name
            serverUrl = server.baseUrl
            apiKey = server.apiKey.orEmpty()
        }
    }

    fun onName(value: String) { serverName = value }
    fun onUrl(value: String) { serverUrl = value; testOk = null; testStatus = null; urlError = null }
    fun onApiKey(value: String) { apiKey = value; testOk = null; testStatus = null }

    private fun currentServer() = ServerConfig(
        id = ServerRepository.DEFAULT_SERVER_ID,
        name = serverName.ifBlank { "My server" },
        baseUrl = serverUrl.trim(),
        apiKey = apiKey.trim().ifBlank { null },
    )

    fun testConnection() {
        val error = baseUrlError(serverUrl)
        if (error != null) {
            testOk = false
            testStatus = error
            return
        }
        viewModelScope.launch {
            testing = true
            testOk = null
            testStatus = null
            val health = api.health(currentServer())
            testOk = health.ok
            testStatus = if (health.ok) "Connected" else (health.message ?: health.status)
            testing = false
        }
    }

    fun goToServer() { step = OnboardingStep.Server }

    fun goToModels() {
        val error = baseUrlError(serverUrl)
        if (error != null) {
            urlError = error
            return
        }
        viewModelScope.launch {
            val server = currentServer()
            serverRepository.upsert(server)
            serverRepository.setActive(server.id)
            step = OnboardingStep.Model
            loadModels()
        }
    }

    fun back() {
        step = when (step) {
            OnboardingStep.Welcome -> OnboardingStep.Welcome
            OnboardingStep.Server -> OnboardingStep.Welcome
            OnboardingStep.Model -> OnboardingStep.Server
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            loadingModels = true
            error = null
            runCatching { api.models(currentServer()) }
                .onSuccess { models = it }
                .onFailure { error = it.message }
            loadingModels = false
        }
    }

    fun selectModel(id: String) {
        viewModelScope.launch {
            selectedModel = id
            loadingModel = true
            error = null
            runCatching { api.loadModel(currentServer(), id) }
                .onFailure { error = it.message }
            loadingModel = false
            loadModels()
        }
    }

    fun finish() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }
}
