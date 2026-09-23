package com.llamacpp.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.data.repo.SettingsRepository
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    serverRepository: ServerRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    val active: StateFlow<ServerConfig?> = serverRepository.activeServer
        .map<ServerConfig, ServerConfig?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hapticsEnabled: StateFlow<Boolean> = settingsRepository.hapticsWhileGenerating
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHapticsWhileGenerating(enabled) }
    }

    fun rerunOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(false) }
    }
}
