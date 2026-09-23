package com.llamacpp.mobile.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.ui.theme.LocalLlamaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Most images that can be attached to one message. */
private const val MAX_ATTACHMENTS = 4
private const val JPEG_QUALITY = 85

/**
 * The composer. The draft is hoisted so it survives rotation and can be
 * pre-filled by starter suggestions; attachments stay internal (data URIs are
 * large and not `Saveable`-friendly).
 *
 * `generatingHere` shows Stop; `generatingElsewhere` keeps Send but disables it,
 * since one generation runs at a time and it belongs to another conversation.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    generatingHere: Boolean,
    generatingElsewhere: Boolean,
    modelSelected: Boolean,
    supportsVision: Boolean,
    onSubmit: (text: String, images: List<String>) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attachments by remember { mutableStateOf(listOf<String>()) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || attachments.size >= MAX_ATTACHMENTS) return@rememberLauncherForActivityResult
        scope.launch {
            // Decode and re-encode off the main thread; an unreadable pick is ignored.
            val dataUri = withContext(Dispatchers.IO) { runCatching { encodeAttachment(context, uri) }.getOrNull() }
            if (dataUri != null && attachments.size < MAX_ATTACHMENTS) attachments = attachments + dataUri
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
                    IconButton(
                        onClick = { pickImage.launch("image/*") },
                        enabled = attachments.size < MAX_ATTACHMENTS,
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, "Attach image")
                    }
                }
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                generatingElsewhere -> "Generating in another chat…"
                                !modelSelected -> "Select a model to chat"
                                else -> "Message"
                            },
                        )
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
                if (generatingHere) {
                    FilledIconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, "Stop")
                    }
                } else {
                    val hasContent = text.isNotBlank() || attachments.isNotEmpty()
                    FilledIconButton(
                        onClick = {
                            if (hasContent) {
                                onSubmit(text.trim(), attachments)
                                attachments = emptyList()
                            }
                        },
                        enabled = modelSelected && !generatingElsewhere && hasContent,
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

/**
 * Reads a picked image bounded to [MAX_IMAGE_EDGE_PX] on its longest edge and
 * returns it as a `data:` URI. JPEG at [JPEG_QUALITY] unless the source is a PNG
 * with transparency, which stays PNG. Throws on an unreadable URI.
 */
private fun encodeAttachment(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: throw IllegalStateException("Cannot open $uri")
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE_PX)
    }
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalStateException("Cannot decode $uri")
    try {
        val keepPng = resolver.getType(uri) == "image/png" && bitmap.hasAlpha()
        val mime = if (keepPng) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream()
        if (keepPng) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } else {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        return "data:$mime;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    } finally {
        bitmap.recycle()
    }
}
