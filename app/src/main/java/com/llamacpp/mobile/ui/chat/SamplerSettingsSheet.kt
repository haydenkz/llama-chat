package com.llamacpp.mobile.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.domain.model.SamplerSettings
import com.llamacpp.mobile.ui.components.LabeledSwitch
import com.llamacpp.mobile.ui.components.SamplerSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplerSettingsSheet(
    settings: SamplerSettings,
    customized: Boolean,
    onChange: (SamplerSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Free-text fields keep their own draft so typing "-" or a blank line is not
    // normalised away mid-keystroke; the model only receives parsed changes.
    var seedText by rememberSaveable { mutableStateOf(settings.seed.toString()) }
    var stopText by rememberSaveable { mutableStateOf(settings.stop.joinToString("\n")) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sampling", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (customized) {
                                "Customized for this model"
                            } else {
                                "Defaults reported by the model"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (customized) {
                        TextButton(onClick = onReset) { Text("Reset") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                Text("System prompt", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = settings.systemPrompt,
                    onValueChange = { onChange(settings.copy(systemPrompt = it)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    minLines = 2,
                    maxLines = 6,
                    placeholder = { Text("You are a helpful assistant…") },
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                SamplerSlider(
                    title = "Temperature",
                    value = settings.temperature,
                    valueRange = 0f..2f,
                    onChange = { onChange(settings.copy(temperature = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Top K",
                    value = settings.topK.toFloat(),
                    valueRange = 0f..200f,
                    steps = 199,
                    onChange = { onChange(settings.copy(topK = it.toInt())) },
                )
            }
            item {
                SamplerSlider(
                    title = "Top P",
                    value = settings.topP,
                    valueRange = 0f..1f,
                    onChange = { onChange(settings.copy(topP = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Min P",
                    value = settings.minP,
                    valueRange = 0f..1f,
                    onChange = { onChange(settings.copy(minP = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Repeat penalty",
                    value = settings.repeatPenalty,
                    valueRange = 1f..2f,
                    onChange = { onChange(settings.copy(repeatPenalty = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Presence penalty",
                    value = settings.presencePenalty,
                    valueRange = -2f..2f,
                    onChange = { onChange(settings.copy(presencePenalty = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Frequency penalty",
                    value = settings.frequencyPenalty,
                    valueRange = -2f..2f,
                    onChange = { onChange(settings.copy(frequencyPenalty = it)) },
                )
            }
            item {
                SamplerSlider(
                    title = "Max tokens",
                    value = settings.maxTokens.toFloat(),
                    valueRange = 0f..32768f,
                    onChange = { onChange(settings.copy(maxTokens = it.toInt())) },
                )
            }
            item {
                OutlinedTextField(
                    value = seedText,
                    onValueChange = { raw ->
                        seedText = raw
                        val parsed = raw.trim().toIntOrNull() ?: if (raw.isBlank()) -1 else return@OutlinedTextField
                        if (parsed != settings.seed) onChange(settings.copy(seed = parsed))
                    },
                    label = { Text("Seed (-1 = random)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true,
                )
            }
            item {
                OutlinedTextField(
                    value = stopText,
                    onValueChange = { raw ->
                        stopText = raw
                        val sequences = raw.split("\n").filter { it.isNotEmpty() }
                        if (sequences != settings.stop) onChange(settings.copy(stop = sequences))
                    },
                    label = { Text("Stop sequences (one per line)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    minLines = 1,
                    maxLines = 4,
                )
            }
            item {
                LabeledSwitch(
                    title = "Show reasoning",
                    description = "Request the model's thinking / reasoning content when supported.",
                    checked = settings.reasoning,
                    onCheckedChange = { onChange(settings.copy(reasoning = it)) },
                )
            }
            item {
                LabeledSwitch(
                    title = "Cache prompt",
                    description = "Reuse the KV cache between turns for faster replies.",
                    checked = settings.cachePrompt,
                    onCheckedChange = { onChange(settings.copy(cachePrompt = it)) },
                )
            }
            item {
                OutlinedTextField(
                    value = settings.jsonSchema,
                    onValueChange = { onChange(settings.copy(jsonSchema = it)) },
                    label = { Text("JSON schema (optional response format)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    minLines = 2,
                    maxLines = 8,
                )
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}
