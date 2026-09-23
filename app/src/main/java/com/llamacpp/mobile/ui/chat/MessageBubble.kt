package com.llamacpp.mobile.ui.chat

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.llamacpp.mobile.data.tools.FileTool
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.ToolCall
import com.llamacpp.mobile.ui.components.StreamingMarkdownText
import com.llamacpp.mobile.ui.theme.CodeTextStyle
import com.llamacpp.mobile.ui.util.formatBytes
import com.llamacpp.mobile.ui.util.formatDuration
import com.llamacpp.mobile.ui.util.formatSpeed
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A run of assistant/tool messages between two user messages. */
data class AssistantTurn(
    val id: Long,
    val messages: List<ChatMessage>,
)

@Composable
fun UserBubble(message: ChatMessage, modifier: Modifier = Modifier) {
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
}

/**
 * Renders an assistant turn in work-mode style: a **thinking** status line
 * (shimmer while working → "Thought for Xs" with a short summary), compact **tool
 * chips**, the **answer** (streams live), then a **Files** section.
 */
@Composable
fun AssistantTurnView(
    turn: AssistantTurn,
    toolResults: Map<String, ChatMessage>,
    isStreaming: Boolean,
    streamingContent: String,
    streamingReasoning: String,
    canRegenerate: Boolean,
    onCopy: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finalAnswer = remember(turn.messages) {
        turn.messages.lastOrNull { it.content.isNotBlank() && it.toolCalls.isEmpty() }
    }
    val allToolCalls = remember(turn.messages) { turn.messages.flatMap { it.toolCalls } }
    val fileCalls = remember(allToolCalls) { allToolCalls.filter { it.name == FileTool.NAME } }
    val error = remember(turn.messages) { turn.messages.firstNotNullOfOrNull { it.error } }

    // Everything the model "worked through": reasoning + text it wrote between tools.
    val detail = remember(turn.messages, streamingReasoning) {
        buildList {
            turn.messages.forEach { message ->
                if (message.reasoning.isNotBlank()) add(message.reasoning)
                if (message.content.isNotBlank() && message !== finalAnswer) add(message.content)
            }
            if (streamingReasoning.isNotBlank()) add(streamingReasoning)
        }.joinToString("\n\n")
    }
    val summary = remember(turn.messages) {
        turn.messages.firstNotNullOfOrNull { it.thinkingSummary }
    }
    val thoughtSeconds = remember(turn.messages) {
        val first = turn.messages.firstOrNull()?.createdAt ?: return@remember 0L
        val last = turn.messages.lastOrNull()?.createdAt ?: first
        ((last - first) / 1000L).coerceAtLeast(0L)
    }
    // "Working" = the model is still going and hasn't produced answer text yet.
    val working = isStreaming && streamingContent.isBlank() && finalAnswer == null

    // Live timer that keeps counting while the model works — including while it
    // runs tools — instead of only updating when a message is persisted.
    var elapsedSeconds by remember { mutableStateOf(0L) }
    val turnStart = remember(turn.id) { System.currentTimeMillis() }
    LaunchedEffect(isStreaming) {
        while (isStreaming) {
            elapsedSeconds = ((System.currentTimeMillis() - turnStart) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (working) {
            ThinkingStatus(working = true, durationSeconds = elapsedSeconds, summary = null, detail = null)
        } else if (detail.isNotBlank()) {
            ThinkingStatus(
                working = false,
                durationSeconds = thoughtSeconds,
                summary = summary,
                detail = detail,
            )
        }

        allToolCalls.filter { it.name != FileTool.NAME }.forEach { call ->
            ToolChip(call = call, result = toolResults[call.id])
        }

        if (isStreaming && streamingContent.isNotEmpty()) {
            StreamingMarkdownText(content = streamingContent, modifier = Modifier.fillMaxWidth())
        } else if (!isStreaming && finalAnswer != null) {
            StreamingMarkdownText(content = finalAnswer.content, modifier = Modifier.fillMaxWidth())
        }

        // Files only appear once the model has finished responding.
        if (!isStreaming && fileCalls.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Files",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            fileCalls.forEach { call ->
                FileRow(call = call, result = toolResults[call.id])
            }
        }

        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        AnimatedVisibility(
            visible = !isStreaming && finalAnswer != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (finalAnswer != null) {
                AnswerActions(
                    message = finalAnswer,
                    canRegenerate = canRegenerate,
                    onCopy = { onCopy(finalAnswer) },
                    onRegenerate = onRegenerate,
                )
            }
        }
    }
}

@Composable
private fun AnswerActions(
    message: ChatMessage,
    canRegenerate: Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    val stats = remember(message) {
        buildList {
            message.model?.let { add(it) }
            message.tokenCount?.let { add("$it tokens") }
            message.durationMs?.let { add(formatDuration(it)) }
            message.tokensPerSecond?.let { add(formatSpeed(it)) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (stats.isNotEmpty()) {
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
        if (message.content.isNotBlank()) {
            IconButton(onClick = onCopy, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", Modifier.size(16.dp))
            }
        }
        if (canRegenerate) {
            IconButton(onClick = onRegenerate, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Refresh, "Regenerate", Modifier.size(18.dp))
            }
        }
    }
}

/** Thinking status line: shimmer while working, then "Thought for Xs" + summary. */
@Composable
private fun ThinkingStatus(
    working: Boolean,
    durationSeconds: Long,
    summary: String?,
    detail: String?,
) {
    var expanded by remember { mutableStateOf(false) }
    val expandedText = summary?.takeIf { it.isNotBlank() } ?: detail
    val canExpand = !working && !expandedText.isNullOrBlank()

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (working) {
                ShimmerText(text = "Thinking", modifier = Modifier.weight(1f))
                Text(
                    text = "${durationSeconds}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = formatThought(durationSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canExpand) {
                    Text(
                        text = if (expanded) "⌄" else "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (canExpand) {
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = expandedText.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** "Thinking" with a sweeping highlight while the model works. */
@Composable
private fun ShimmerText(text: String, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.onSurfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface
    val sweep = 600f
    val start = progress * (sweep * 2f) - sweep
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + sweep, 0f),
    )
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(brush = brush),
        modifier = modifier,
    )
}

private fun formatThought(seconds: Long): String = when {
    seconds < 1L -> "Thought briefly"
    seconds < 60L -> "Thought for ${seconds}s"
    else -> "Thought for ${seconds / 60}m ${seconds % 60}s"
}

/**
 * A compact, collapsible tool chip. Web search keeps its favicons; other tools show
 * a one-line input and the output.
 */
@Composable
private fun ToolChip(call: ToolCall, result: ChatMessage?) {
    val label = toolLabel(call.name)
    val summary = toolSummary(call.arguments)
    val resultText = result?.content
    val running = result == null
    var expanded by remember { mutableStateOf(false) }

    val isWeb = call.name == WebSearchTool.NAME
    val results = remember(resultText, isWeb) {
        if (isWeb) resultText?.let(::parseSearchResults).orEmpty() else emptyList()
    }
    val hosts = remember(results) { results.map { it.host }.filter { it.isNotBlank() }.distinct() }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = resultText != null) { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = toolIcon(call.name),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (resultText != null) {
                    Text(
                        text = if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (isWeb && resultText != null && !expanded && hosts.isNotEmpty()) {
                FaviconRow(
                    hosts = hosts,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                ) {
                    if (isWeb && results.isNotEmpty()) {
                        results.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                            SearchResultRow(item)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        ) {
                            Text(
                                text = resultText.orEmpty().ifBlank { "(no output)" },
                                style = CodeTextStyle,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A downloadable file produced by the model. */
@Composable
private fun FileRow(call: ToolCall, result: ChatMessage?) {
    val context = LocalContext.current
    val filename = remember(call.arguments) {
        jsonArg(call.arguments, "filename").orEmpty().ifBlank { "file.txt" }
    }
    val content = remember(call.arguments) { jsonArg(call.arguments, "content").orEmpty() }
    val sizeBytes = remember(content) { content.toByteArray(Charsets.UTF_8).size.toLong() }
    val mime = remember(filename) { mimeFor(filename) }
    val created = result?.content?.startsWith("Created") == true
    val working = result == null

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (working) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        working -> "Working on file…"
                        !created -> result.content.lineSequence().firstOrNull().orEmpty().take(80)
                        else -> formatBytes(sizeBytes)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = { saveLauncher.launch(filename) },
                enabled = content.isNotBlank() && !working,
            ) { Text("Save") }
        }
    }
}

private data class SearchResult(val title: String, val url: String, val snippet: String) {
    val host: String get() = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
}

@Composable
private fun SearchResultRow(result: SearchResult) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openUrl(context, result.url) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Favicon(host = result.host, index = 0, size = 20.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (result.host.isNotBlank()) {
                Text(
                    text = result.host,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.snippet.isNotBlank()) {
                Text(
                    text = result.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FaviconRow(hosts: List<String>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    LaunchedEffect(hosts) {
        val loader = coil3.SingletonImageLoader.get(context)
        hosts.take(6).forEach { host ->
            runCatching {
                loader.enqueue(ImageRequest.Builder(context).data(faviconUrl(host)).build())
            }
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hosts.take(6).forEachIndexed { index, host ->
            Favicon(host = host, index = index, size = 20.dp)
        }
    }
}

@Composable
private fun Favicon(host: String, index: Int, size: androidx.compose.ui.unit.Dp) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        visible = true
    }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
        animationSpec = tween(durationMillis = 220),
        label = "faviconScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "faviconAlpha",
    )
    if (host.isBlank()) return
    AsyncImage(
        model = faviconUrl(host),
        contentDescription = host,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private fun faviconUrl(host: String): String = "https://icons.duckduckgo.com/ip3/$host.ico"

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Parses the "1. Title\n<url>\nsnippet" blocks returned by the search tool. */
private fun parseSearchResults(text: String): List<SearchResult> {
    val results = mutableListOf<SearchResult>()
    var title: String? = null
    var url: String? = null
    val snippet = StringBuilder()

    fun flush() {
        if (title != null || url != null) {
            results.add(
                SearchResult(
                    title = title.orEmpty().ifBlank { "Result ${results.size + 1}" },
                    url = url.orEmpty(),
                    snippet = snippet.toString().trim(),
                ),
            )
        }
        title = null
        url = null
        snippet.clear()
    }

    text.lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.isEmpty() -> Unit
            NUMERIC_ENTRY.matches(line) -> {
                flush()
                title = NUMERIC_ENTRY.find(line)?.groupValues?.get(1)?.trim().orEmpty()
            }
            line.startsWith("http://") || line.startsWith("https://") -> url = line
            title != null -> {
                if (snippet.isNotEmpty()) snippet.append(' ')
                snippet.append(line)
            }
        }
    }
    flush()
    return results
}

private val NUMERIC_ENTRY = Regex("^\\d+\\.\\s*(.*)$")

private fun mimeFor(filename: String): String = when (filename.substringAfterLast('.', "").lowercase()) {
    "txt" -> "text/plain"
    "md" -> "text/markdown"
    "csv" -> "text/csv"
    "json" -> "application/json"
    "html", "htm" -> "text/html"
    "xml" -> "text/xml"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "pdf" -> "application/pdf"
    "py" -> "text/x-python"
    "kt" -> "text/x-kotlin"
    "js" -> "text/javascript"
    "sh" -> "text/x-shellscript"
    else -> "application/octet-stream"
}

private val toolJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Parses a string argument from a tool-call arguments JSON. */
private fun jsonArg(arguments: String, key: String): String? =
    runCatching {
        toolJson.parseToJsonElement(arguments).jsonObject[key]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

/** A short human-readable summary of a tool call's arguments. */
private fun toolSummary(arguments: String): String {
    for (key in listOf("query", "location", "expression", "code", "filename", "input", "text")) {
        jsonArg(arguments, key)?.let { value ->
            val firstLine = value.lineSequence().firstOrNull()?.trim().orEmpty()
            if (firstLine.isNotBlank()) return firstLine.take(80)
        }
    }
    return arguments.trim().trim('{', '}', ' ').take(80)
}

private fun toolLabel(name: String): String = when (name) {
    WebSearchTool.NAME -> "Searched the web"
    "get_current_time" -> "Checked the time"
    "get_weather" -> "Checked the weather"
    "wikipedia_summary" -> "Looked up Wikipedia"
    "calculate" -> "Calculated"
    "run_python" -> "Ran Python"
    FileTool.NAME -> "Created a file"
    else -> name
}

private fun toolIcon(name: String) = when (name) {
    WebSearchTool.NAME -> Icons.Default.Search
    "get_current_time" -> Icons.Default.Schedule
    "get_weather" -> Icons.Default.Cloud
    "wikipedia_summary" -> Icons.Default.MenuBook
    "calculate" -> Icons.Default.Calculate
    "run_python" -> Icons.Default.Terminal
    FileTool.NAME -> Icons.Default.InsertDriveFile
    else -> Icons.Default.Build
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
