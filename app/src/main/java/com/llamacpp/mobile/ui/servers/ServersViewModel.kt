package com.llamacpp.mobile.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ServerHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ServersViewModel(
    private val repository: ServerRepository,
    private val api: LlamaApi,
) : ViewModel() {

    val servers: StateFlow<List<ServerConfig>> = repository.servers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val active: StateFlow<ServerConfig?> = repository.activeServer
        .map<ServerConfig, ServerConfig?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())
    val health: StateFlow<Map<String, ServerHealth>> = _health.asStateFlow()

    fun save(server: ServerConfig) {
        viewModelScope.launch { repository.upsert(server) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setActive(id: String) {
        viewModelScope.launch { repository.setActive(id) }
    }

    fun test(server: ServerConfig) {
        viewModelScope.launch {
            _health.update { it + (server.id to api.health(server)) }
        }
    }
}
