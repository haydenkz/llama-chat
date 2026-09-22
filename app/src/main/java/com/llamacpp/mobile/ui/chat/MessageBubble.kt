package com.llamacpp.mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.ToolCall
import com.llamacpp.mobile.ui.components.MarkdownText
import com.llamacpp.mobile.ui.components.StreamingMarkdownText
import com.llamacpp.mobile.ui.util.formatDuration
import com.llamacpp.mobile.ui.util.formatSpeed

@Composable
fun MessageBubble(
    message: ChatMessage,
    isStreaming: Boolean = false,
    canRegenerate: Boolean = false,
    onCopy: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val copy: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
        onCopy()
    }

    if (message.role == ChatRole.User) {
        Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Spacer(Modifier.weight(1f))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    message.images.forEach { uri ->
                        DataUriImage(uri, Modifier.fillMaxWidth().padding(bottom = 6.dp))
                    }
                    if (message.content.isNotBlank()) {
                        Text(text = message.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        return
    }

    if (message.role == ChatRole.Tool) {
        ToolResultCard(message, modifier)
        return
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        message.toolCalls.forEach { call -> ToolCallCard(call) }
        if (message.reasoning.isNotBlank()) {
            ReasoningBlock(
                reasoning = message.reasoning,
                streaming = isStreaming,
            )
        }

        when {
            isStreaming -> {
                if (message.content.isNotEmpty()) {
                    StreamingMarkdownText(
                        content = message.content,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            message.content.isNotBlank() -> {
                MarkdownText(content = message.content, modifier = Modifier.fillMaxWidth())
            }
        }

        if (message.error != null) {
            if (message.content.isNotBlank()) Spacer(Modifier.height(4.dp))
            Text(
                text = message.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        val stats = remember(message) {
            buildList {
                message.model?.let { add(it) }
                message.tokenCount?.let { add("$it tokens") }
                message.durationMs?.let { add(formatDuration(it)) }
                message.tokensPerSecond?.let { add(formatSpeed(it)) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isStreaming && stats.isNotEmpty()) {
                Text(
                    text = stats.joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (!isStreaming && message.content.isNotBlank()) {
                IconButton(onClick = copy, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp))
                }
            }
            if (canRegenerate && onRegenerate != null && !isStreaming) {
                IconButton(onClick = onRegenerate, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Refresh, "Regenerate", Modifier.size(18.dp))
                }
            }
            if (onEdit != null && !isStreaming) {
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, "Edit", Modifier.size(16.dp))
                }
            }
            if (!isStreaming) {
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        "Delete",
                        Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(streaming) }
    var userToggled by remember { mutableStateOf(false) }

    // Stay fully expanded for the whole generation so the block never collapses
    // mid-stream (that height collapse was yanking the scroll position). It
    // collapses once the response is complete, or when the user toggles it.
    LaunchedEffect(streaming) {
        if (!userToggled) expanded = streaming
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        userToggled = true
                        expanded = !expanded
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = when {
                        expanded -> "Hide thinking"
                        streaming -> "Thinking…"
                        else -> "Show thinking"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (expanded) {
                Text(
                    text = reasoning,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolCallCard(call: ToolCall) {
    val query = remember(call.arguments) { toolArgument(call.arguments, "query") }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = when (call.name) {
                        "web_search" -> "Web search"
                        else -> call.name
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                if (query.isNotBlank()) {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolResultCard(message: ChatMessage, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val lines = remember(message.content) { message.content.lines().filter { it.isNotBlank() }.size }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    text = if (expanded) "Hide results" else "Search results ($lines lines)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(if (expanded) "▾" else "▸", style = MaterialTheme.typography.labelMedium)
            }
            if (expanded) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                )
            }
        }
    }
}

/** Best-effort extraction of an argument from a JSON-ish tool-call arguments string. */
private fun toolArgument(arguments: String, key: String): String {
    val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(arguments)
    return match?.groupValues?.get(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        .orEmpty()
        .ifBlank { arguments.trim().trim('{', '}', ' ') }
}

@Composable
fun DataUriImage(dataUri: String, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap? = remember(dataUri) {
        runCatching {
            val encoded = dataUri.substringAfter("base64,", "")
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    }
}
