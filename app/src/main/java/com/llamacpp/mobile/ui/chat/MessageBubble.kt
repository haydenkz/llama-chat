package com.llamacpp.mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
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
import coil3.compose.AsyncImage
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
    toolResults: Map<String, ChatMessage> = emptyMap(),
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

    if (message.role == ChatRole.Tool) return

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

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        message.toolCalls.forEach { call ->
            WebSearchCard(call = call, result = toolResults[call.id] ?: toolResults[call.id.ifBlank { "" }])
        }

        if (message.reasoning.isNotBlank()) {
            ReasoningBlock(reasoning = message.reasoning, streaming = isStreaming)
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
        val isToolOnly = message.content.isBlank() && message.reasoning.isBlank() &&
            message.toolCalls.isNotEmpty()

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isStreaming && !isToolOnly && stats.isNotEmpty()) {
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
            if (!isStreaming && !isToolOnly && message.content.isNotBlank()) {
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

/**
 * A single web-search card. Collapsed it shows the query, a result count and the
 * result favicons; tapping it expands the full results inline — instead of a
 * separate call card, stats line and results bubble.
 */
@Composable
private fun WebSearchCard(call: ToolCall, result: ChatMessage?) {
    val query = remember(call.arguments) { toolArgument(call.arguments, "query") }
    val resultText = result?.content
    val hosts = remember(resultText) { resultText?.let(::extractHosts).orEmpty() }
    val resultCount = remember(resultText) { resultText?.let(::countResults) ?: 0 }
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = resultText != null) { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Web search",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (query.isNotBlank()) {
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (resultText == null) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    if (resultCount > 0) {
                        Text(
                            text = "$resultCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (resultText != null && !expanded && hosts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    hosts.take(6).forEach { host ->
                        Favicon(host)
                    }
                }
            }

            if (resultText != null && expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = resultText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Favicon(host: String) {
    AsyncImage(
        model = "https://icons.duckduckgo.com/ip3/$host.ico",
        contentDescription = host,
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** Extracts distinct hosts from the "…\n<url>\n…" lines of a search result. */
private fun extractHosts(text: String): List<String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith("http://") || it.startsWith("https://") }
        .mapNotNull { runCatching { java.net.URI(it).host?.removePrefix("www.") }.getOrNull() }
        .distinct()
        .toList()

private fun countResults(text: String): Int =
    Regex("(?m)^\\d+\\.\\s").findAll(text).count()

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
