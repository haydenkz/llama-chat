package com.llamacpp.mobile.ui.servers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.data.repo.ServerRepository
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.ui.appVmFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class ServerEditViewModel(
    private val repository: ServerRepository,
    private val serverId: String?,
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    var baseUrl by mutableStateOf("http://")
        private set
    var apiKey by mutableStateOf("")
        private set
    var isNew by mutableStateOf(serverId == null)
        private set

    init {
        if (serverId != null) {
            viewModelScope.launch {
                repository.servers.first().firstOrNull { it.id == serverId }?.let { server ->
                    name = server.name
                    baseUrl = server.baseUrl
                    apiKey = server.apiKey.orEmpty()
                }
            }
        }
    }

    fun onName(value: String) { name = value }
    fun onBaseUrl(value: String) { baseUrl = value }
    fun onApiKey(value: String) { apiKey = value }

    val canSave: Boolean get() = name.isNotBlank() && baseUrl.isNotBlank()

    fun save(onSaved: () -> Unit) {
        if (!canSave) return
        val server = ServerConfig(
            id = serverId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            baseUrl = baseUrl.trim(),
            apiKey = apiKey.trim().ifBlank { null },
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    container: AppContainer,
    serverId: String?,
    onBack: () -> Unit,
) {
    val vm: ServerEditViewModel = viewModel(
        factory = appVmFactory(container) { ServerEditViewModel(it.serverRepository, serverId) },
    )
    val name = vm.name
    val baseUrl = vm.baseUrl
    val apiKey = vm.apiKey

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = vm::onName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = vm::onBaseUrl,
                label = { Text("Base URL") },
                placeholder = { Text("http://192.168.2.3:8080") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = apiKey,
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
