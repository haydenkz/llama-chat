package com.llamacpp.mobile.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.ui.appVmFactory
import com.llamacpp.mobile.ui.chat.ModelPickerList

@Composable
fun OnboardingScreen(container: AppContainer) {
    val vm: OnboardingViewModel = viewModel(
        factory = appVmFactory(container) {
            OnboardingViewModel(it.serverRepository, it.settingsRepository, it.api)
        },
    )

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(24.dp),
        ) {
            StepDots(vm.step.ordinal, OnboardingStep.entries.size)

            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp)) {
                when (vm.step) {
                    OnboardingStep.Welcome -> WelcomeStep()
                    OnboardingStep.Server -> ServerStep(vm)
                    OnboardingStep.Model -> ModelStep(vm)
                }
            }

            when (vm.step) {
                OnboardingStep.Welcome -> {
                    Button(onClick = vm::goToServer, modifier = Modifier.fillMaxWidth()) {
                        Text("Get started")
                    }
                }

                OnboardingStep.Server -> Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = vm::back) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = vm::goToModels,
                        enabled = vm.serverUrl.isNotBlank(),
                    ) { Text("Continue") }
                }

                OnboardingStep.Model -> Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = vm::back) { Text("Back") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::finish) { Text("Skip") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = vm::finish) { Text("Start chatting") }
                }
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Spacer(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Welcome to LlamaChat",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Chat with local models running on your llama.cpp server. " +
                "Let's connect it and pick a model.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ServerStep(vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Connect your server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "The app talks to llama.cpp's llama-server. Enter its address on your network.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = vm.serverUrl,
            onValueChange = vm::onUrl,
            label = { Text("Server URL") },
            placeholder = { Text("http://<your-server>:8080") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.serverName,
            onValueChange = vm::onName,
            label = { Text("Name (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.apiKey,
            onValueChange = vm::onApiKey,
            label = { Text("API key (optional)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = vm::testConnection,
                enabled = !vm.testing && vm.serverUrl.isNotBlank(),
            ) { Text("Test connection") }
            Spacer(Modifier.width(12.dp))
            if (vm.testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            vm.testOk?.let { ok ->
                Icon(
                    imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = vm.testStatus.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ModelStep(vm: OnboardingViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text("Pick a model", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Selecting a model loads it now so your first chat is instant. You can change it later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        vm.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        ModelPickerList(
            models = vm.models,
            selectedId = vm.selectedModel,
            loading = vm.loadingModels || vm.loadingModel,
            onSelect = vm::selectModel,
            onRefresh = vm::loadModels,
            pendingIds = vm.selectedModel?.takeIf { vm.loadingModel }?.let { setOf(it) }.orEmpty(),
        )
    }
}
