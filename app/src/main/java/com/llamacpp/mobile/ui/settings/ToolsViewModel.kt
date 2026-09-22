package com.llamacpp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.data.tools.ChatTool
import com.llamacpp.mobile.data.tools.ToolRegistry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ToolsViewModel(
    registry: ToolRegistry,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val tools: List<ChatTool> = registry.all()

    /** Tool names the user has switched off (all tools are on by default). */
    val disabled: StateFlow<Set<String>> = settingsRepository.disabledTools
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setToolEnabled(name, enabled) }
    }
}
