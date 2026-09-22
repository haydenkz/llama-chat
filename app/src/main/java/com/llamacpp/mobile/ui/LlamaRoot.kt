package com.llamacpp.mobile.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ThemeMode
import com.llamacpp.mobile.ui.navigation.AppNavHost
import com.llamacpp.mobile.ui.theme.LlamaChatTheme

@Composable
fun LlamaRoot(container: AppContainer) {
    val themeMode by container.settingsRepository.themeMode
        .collectAsStateWithLifecycle(initialValue = ThemeMode.System)

    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    LlamaChatTheme(darkTheme = darkTheme) {
        AppNavHost(container)
    }
}
