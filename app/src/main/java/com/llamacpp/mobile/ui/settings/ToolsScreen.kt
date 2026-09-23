package com.llamacpp.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.ui.appVmFactory
import com.llamacpp.mobile.ui.components.LabeledSwitch
import com.llamacpp.mobile.ui.components.SectionCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val vm: ToolsViewModel = viewModel(
        factory = appVmFactory(container) { ToolsViewModel(it.toolRegistry, it.settingsRepository) },
    )
    val disabled by vm.disabled.collectAsStateWithLifecycle()
    val pistonUrl by vm.pistonUrl.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard(title = "Model tools") {
                Text(
                    text = "Tools the model can call while answering. All are on by default. " +
                        "Tool calling requires the model to be served with --jinja.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))

            SectionCard(title = "Python code execution") {
                Text(
                    text = "Python runs in a self-hosted Piston sandbox on your server. " +
                        "Leave the URL blank to use Piston on the llama-server host at port 2000.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pistonUrl,
                    onValueChange = vm::setPistonUrl,
                    label = { Text("Piston URL (optional)") },
                    placeholder = { Text("http://<your-server>:2000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            vm.tools.forEach { tool ->
                SectionCard {
                    LabeledSwitch(
                        title = tool.displayName,
                        description = tool.description,
                        checked = tool.name !in disabled,
                        onCheckedChange = { vm.setEnabled(tool.name, it) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
