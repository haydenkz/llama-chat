package com.llamacpp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.BuildConfig
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.ThemeMode
import com.llamacpp.mobile.ui.appVmFactory
import com.llamacpp.mobile.ui.components.LabeledSwitch
import com.llamacpp.mobile.ui.components.LabeledValue
import com.llamacpp.mobile.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onManageServers: () -> Unit,
    onOpenTools: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(
        factory = appVmFactory(container) { SettingsViewModel(it.settingsRepository, it.serverRepository) },
    )
    val themeMode by vm.themeMode.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val hapticsEnabled by vm.hapticsEnabled.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionCard(title = "Appearance") {
                Text("Theme", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(mode.name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Feedback") {
                LabeledSwitch(
                    title = "Haptics while generating",
                    description = "Subtle tick as tokens stream in, like the ChatGPT app.",
                    checked = hapticsEnabled,
                    onCheckedChange = vm::setHaptics,
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Tools") {
                Text(
                    text = "Choose which tools the model can call: web search, weather, Wikipedia, calculator and date & time.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenTools, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage tools")
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Active server") {
                LabeledValue("Name", active?.name ?: "—")
                LabeledValue("Base URL", active?.normalizedBaseUrl ?: "—")
                LabeledValue("API key", if (active?.apiKey.isNullOrBlank()) "none" else "set")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onManageServers, modifier = Modifier.fillMaxWidth()) {
                    Text("Manage servers")
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "About") {
                LabeledValue("App", "LlamaChat")
                LabeledValue("Version", BuildConfig.VERSION_NAME)
                LabeledValue("API", "llama.cpp OpenAI-compatible")
                LabeledValue("Server default", ServerConfig("", "", "http://192.168.2.3:8080").normalizedBaseUrl)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
