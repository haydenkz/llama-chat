package com.llamacpp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.ChatTool
import com.llamacpp.mobile.data.tools.ToolRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolsViewModel(
    registry: ToolRegistry,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val tools: List<ChatTool> = registry.all()

    /** Tool names the user has switched off (all tools are on by default). */
    val disabled: StateFlow<Set<String>> = settingsRepository.disabledTools
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Owned here rather than read from DataStore on every keystroke: the field
    // needs one immediate source of truth, and writes are debounced.
    private val _pistonUrl = MutableStateFlow("")
    val pistonUrl: StateFlow<String> = _pistonUrl.asStateFlow()
    private var persistJob: Job? = null

    init {
        viewModelScope.launch {
            _pistonUrl.value = runCatching { settingsRepository.pistonUrl.first() }.getOrDefault("")
        }
    }

    fun setEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setToolEnabled(name, enabled) }
    }

    fun setPistonUrl(url: String) {
        _pistonUrl.value = url
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            settingsRepository.setPistonUrl(url)
        }
    }

    private companion object {
        const val PERSIST_DEBOUNCE_MS = 400L
    }
}
