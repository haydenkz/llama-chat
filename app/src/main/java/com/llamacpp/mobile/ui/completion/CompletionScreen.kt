package com.llamacpp.mobile.ui.completion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.ui.appVmFactory
import com.llamacpp.mobile.ui.components.ModelDropdown
import com.llamacpp.mobile.ui.components.SectionCard
import com.llamacpp.mobile.ui.components.SamplerSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletionScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: CompletionViewModel = viewModel(
        factory = appVmFactory(container) { CompletionViewModel(it.api, it.serverRepository) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Completion") },
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
            ModelDropdown(
                models = state.models,
                selectedId = state.selectedModel,
                onSelect = vm::onModel,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.prompt,
                onValueChange = vm::onPrompt,
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 12,
            )
            Spacer(Modifier.height(8.dp))
            SamplerSlider(
                title = "Temperature",
                value = state.temperature,
                valueRange = 0f..2f,
                onChange = vm::onTemperature,
            )
            SamplerSlider(
                title = "Max tokens",
                value = state.maxTokens.toFloat(),
                valueRange = 0f..4096f,
                onChange = { vm.onMaxTokens(it.toInt()) },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isStreaming) {
                    OutlinedButton(onClick = vm::stop) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.height(0.dp))
                        Text("  Stop")
                    }
                } else {
                    Button(
                        onClick = vm::run,
                        enabled = state.prompt.isNotBlank() && state.selectedModel != null,
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("  Run")
                    }
                }
            }
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            if (state.output.isNotBlank() || state.isStreaming) {
                SectionCard(title = "Output") {
                    SelectionContainer {
                        Text(
                            text = state.output.ifBlank { "…" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}
