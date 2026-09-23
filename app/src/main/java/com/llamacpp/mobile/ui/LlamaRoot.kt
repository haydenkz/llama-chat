package com.llamacpp.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ThemeMode
import com.llamacpp.mobile.ui.navigation.AppNavHost
import com.llamacpp.mobile.ui.onboarding.OnboardingScreen
import com.llamacpp.mobile.ui.theme.LlamaChatTheme
import kotlinx.coroutines.flow.catch

@Composable
fun LlamaRoot(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode
        .catch { emit(ThemeMode.System) }
        .collectAsStateWithLifecycle(initialValue = ThemeMode.System)

    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    LlamaChatTheme(darkTheme = darkTheme) {
        val onboardingCompleted by container.settingsRepository.onboardingCompleted
            .catch { emit(false) }
            .collectAsStateWithLifecycle(initialValue = null)

        when (onboardingCompleted) {
            null -> Box(Modifier.fillMaxSize())
            false -> OnboardingScreen(container)
            true -> AppNavHost(container)
        }
    }
}
