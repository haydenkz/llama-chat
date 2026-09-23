package com.llamacpp.mobile.ui.serverinfo

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.llamacpp.mobile.ui.components.InfoChip
import com.llamacpp.mobile.ui.components.LabeledValue
import com.llamacpp.mobile.ui.components.ModelDropdown
import com.llamacpp.mobile.ui.components.SectionCard
import com.llamacpp.mobile.ui.util.formatBytes
import com.llamacpp.mobile.ui.util.formatParams
import com.llamacpp.mobile.ui.util.formatTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInfoScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: ServerInfoViewModel = viewModel(
        factory = appVmFactory(container) { ServerInfoViewModel(it.api, it.serverRepository) },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        Icon(Icons.Default.Refresh, "Refresh")
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
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            SectionCard(title = "Connection") {
                LabeledValue("Server", state.server?.name ?: "—")
                LabeledValue("Base URL", state.server?.normalizedBaseUrl ?: "—")
                LabeledValue("Role", state.props?.role ?: "—")
                LabeledValue("Build", state.props?.buildInfo ?: "—")
                LabeledValue("Model alias", state.props?.modelAlias ?: "—")
                LabeledValue("Model path", state.props?.modelPath ?: "—")
                LabeledValue("Max instances", state.props?.maxInstances?.toString() ?: "—")
                LabeledValue("Autoload", state.props?.modelsAutoload?.toString() ?: "—")
                LabeledValue("CORS proxy", state.props?.corsProxyEnabled?.toString() ?: "—")
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Models (${state.models.size})") {
                state.models.forEach { model ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = listOfNotNull(
                                    model.meta?.nParams?.let { "${formatParams(it)} params" },
                                    model.meta?.sizeBytes?.let { formatBytes(it) },
                                    model.meta?.nCtx?.let { "ctx ${formatTokens(it)}" },
                                    model.meta?.ftype,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InfoChip(model.status ?: "unknown")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            ModelDropdown(
                models = state.models,
                selectedId = state.selectedModel,
                onSelect = vm::selectModel,
            )

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Slots") {
                val slotsError = state.slotsError
                if (slotsError != null) {
                    Text(
                        text = slotsError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    JsonBlock(state.slotsJson ?: "—")
                }
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Metrics") {
                if (state.metricsError != null) {
                    Text(
                        text = state.metricsError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    JsonBlock(state.metricsText ?: "—")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun JsonBlock(text: String) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
