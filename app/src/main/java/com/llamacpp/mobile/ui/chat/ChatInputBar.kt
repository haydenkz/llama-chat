package com.llamacpp.mobile.ui.chat

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.ui.theme.LocalLlamaColors

@Composable
fun ChatInputBar(
    isStreaming: Boolean,
    modelSelected: Boolean,
    supportsVision: Boolean,
    onSend: (String, List<String>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<String>()) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                attachments = attachments + dataUri
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    attachments.forEachIndexed { index, uri ->
                        Box {
                            DataUriImage(uri, Modifier.size(56.dp))
                            IconButton(
                                onClick = { attachments = attachments.filterIndexed { i, _ -> i != index } },
                                modifier = Modifier.size(20.dp).align(Alignment.TopEnd),
                            ) {
                                Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                if (supportsVision) {
                    IconButton(onClick = { pickImage.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, "Attach image")
                    }
                }
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (modelSelected) "Message" else "Select a model to chat")
                    },
                    maxLines = 6,
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LocalLlamaColors.current.muted,
                        unfocusedContainerColor = LocalLlamaColors.current.muted,
                        disabledContainerColor = LocalLlamaColors.current.muted,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                Spacer(Modifier.width(6.dp))
                if (isStreaming) {
                    FilledIconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, "Stop")
                    }
                } else {
                    FilledIconButton(
                        onClick = {
                            if (text.isNotBlank() || attachments.isNotEmpty()) {
                                onSend(text.trim(), attachments)
                                text = ""
                                attachments = emptyList()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        },
                        enabled = modelSelected && (text.isNotBlank() || attachments.isNotEmpty()),
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}
