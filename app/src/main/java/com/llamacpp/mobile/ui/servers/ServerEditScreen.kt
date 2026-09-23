package com.llamacpp.mobile.ui.servers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.data.remote.LlamaApi
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.domain.model.baseUrlError
import com.llamacpp.mobile.ui.appVmFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ServerEditViewModel(
    private val repository: ServerRepository,
    private val api: LlamaApi,
    private val serverId: String?,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var baseUrl by mutableStateOf("http://")
        private set
    var apiKey by mutableStateOf("")
        private set
    var urlError by mutableStateOf<String?>(null)
        private set
    var isNew by mutableStateOf(serverId == null)
        private set

    var hiddenModels by mutableStateOf<Set<String>>(emptySet())
        private set

    var models by mutableStateOf<List<LlamaModel>>(emptyList())
        private set
    var loadingModels by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    init {
        if (serverId != null) {
            viewModelScope.launch {
                repository.servers.first().firstOrNull { it.id == serverId }?.let { server ->
                    name = server.name
                    baseUrl = server.baseUrl
                    apiKey = server.apiKey.orEmpty()
                    hiddenModels = server.hiddenModels.toSet()
                }
            }
        }
    }

    fun onName(value: String) { name = value }
    fun onBaseUrl(value: String) { baseUrl = value; urlError = null }
    fun onApiKey(value: String) { apiKey = value }

    val canSave: Boolean get() = name.isNotBlank() && baseUrl.isNotBlank()

    fun setVisible(modelId: String, visible: Boolean) {
        hiddenModels = if (visible) hiddenModels - modelId else hiddenModels + modelId
    }

    fun loadModels() {
        val target = currentFormAsServer()
        val error = baseUrlError(target.baseUrl)
        if (error != null) {
            modelsError = error
            return
        }
        viewModelScope.launch {
            loadingModels = true
            modelsError = null
            runCatching { api.models(target) }
                .onSuccess { models = it; loadingModels = false }
                .onFailure { modelsError = it.message; loadingModels = false }
        }
    }

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        urlError = baseUrlError(baseUrl)
        if (urlError != null) return
        val server = currentFormAsServer().copy(
            id = serverId ?: UUID.randomUUID().toString(),
            hiddenModels = hiddenModels.toList(),
        )
        viewModelScope.launch {
            repository.upsert(server)
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = serverId ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }

    private fun currentFormAsServer() = ServerConfig(
        id = serverId ?: "draft",
        name = name.ifBlank { "Server" },
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim().ifBlank { null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    container: AppContainer,
    serverId: String?,
    onBack: () -> Unit,
) {
    val vm: ServerEditViewModel = viewModel(
        factory = appVmFactory(container) {
            ServerEditViewModel(it.serverRepository, it.api, serverId)
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.isNew) "Add server" else "Edit server") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = vm::onName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.baseUrl,
                onValueChange = vm::onBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("http://<your-server>:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            vm.urlError?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.apiKey,
                onValueChange = vm::onApiKey,
                label = { Text("API key (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "The app talks to llama.cpp's OpenAI-compatible API. " +
                    "Cleartext HTTP is allowed for LAN addresses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Models", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Toggle a model off to hide it from the model picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vm.loadingModels) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                OutlinedButton(
                    onClick = vm::loadModels,
                    enabled = vm.baseUrl.isNotBlank() && !vm.loadingModels,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(if (vm.models.isEmpty()) "Load" else "Refresh")
                }
            }

            vm.modelsError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            vm.models.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = model.id !in vm.hiddenModels,
                        onCheckedChange = { visible -> vm.setVisible(model.id, visible) },
                    )
                }
            }
            if (vm.models.isEmpty() && vm.modelsError == null && !vm.loadingModels) {
                Text(
                    text = "Tap Load to list this server's models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save(onBack) },
                enabled = vm.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            if (!vm.isNew) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { vm.delete(onBack) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete server") }
            }
        }
    }
}
