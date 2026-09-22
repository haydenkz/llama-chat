package com.llamacpp.mobile.ui.chat

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.ToolCall
import com.llamacpp.mobile.ui.components.MarkdownText
import com.llamacpp.mobile.ui.components.StreamingMarkdownText
import com.llamacpp.mobile.ui.util.formatDuration
import com.llamacpp.mobile.ui.util.formatSpeed
import kotlinx.coroutines.delay

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
 * Renders a whole assistant turn as three clearly separated zones:
 * a single merged **thinking** block, any **tool calls**, then the **answer**
 * (with the stats/actions row only once the answer is complete). This keeps
 * thinking from being split across tool iterations and keeps the generated
 * answer distinct from the tool plumbing.
 */
@Composable
fun AssistantTurnView(
    turn: AssistantTurn,
    toolResults: Map<String, ChatMessage>,
    isStreaming: Boolean,
    streamingContent: String,
    streamingReasoning: String,
    listState: LazyListState?,
    canRegenerate: Boolean,
    onCopy: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thinking = remember(turn.messages, streamingReasoning) {
        buildList {
            turn.messages.forEach { if (it.reasoning.isNotBlank()) add(it.reasoning) }
            if (streamingReasoning.isNotBlank()) add(streamingReasoning)
        }.joinToString("\n\n")
    }
    val toolCalls = remember(turn.messages) { turn.messages.flatMap { it.toolCalls } }
    val answer = remember(turn.messages) { turn.messages.lastOrNull { it.content.isNotBlank() } }
    val error = remember(turn.messages) { turn.messages.firstNotNullOfOrNull { it.error } }
    val answerText = if (isStreaming) streamingContent else answer?.content.orEmpty()

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (thinking.isNotBlank()) {
            ThinkingBlock(
                reasoning = thinking,
                streaming = isStreaming && answerText.isBlank(),
            )
        }

        val webCalls = toolCalls.filter { it.name == WebSearchTool.NAME }
        val otherCalls = toolCalls.filter { it.name != WebSearchTool.NAME }
        if (webCalls.isNotEmpty()) {
            WebSearchCard(
                calls = webCalls.map { call -> call to toolResults[call.id] },
                listState = listState,
            )
        }
        otherCalls.forEach { call ->
            GenericToolCard(
                call = call,
                result = toolResults[call.id],
                listState = listState,
            )
        }

        if (isStreaming && answerText.isEmpty() && thinking.isBlank() && toolCalls.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Thinking…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            isStreaming && answerText.isNotEmpty() -> {
                StreamingMarkdownText(content = answerText, modifier = Modifier.fillMaxWidth())
            }
            answerText.isNotEmpty() -> {
                MarkdownText(content = answerText, modifier = Modifier.fillMaxWidth())
            }
        }

        if (error != null) {
            if (answerText.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Stats + actions only once the answer is finished.
        if (!isStreaming && answer != null) {
            AnswerActions(
                message = answer,
                canRegenerate = canRegenerate,
                onCopy = { onCopy(answer) },
                onRegenerate = onRegenerate,
            )
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

@Composable
private fun ThinkingBlock(reasoning: String, streaming: Boolean) {
    var expanded by remember { mutableStateOf(streaming) }
    var userToggled by remember { mutableStateOf(false) }
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

private data class SearchResult(val title: String, val url: String, val snippet: String) {
    val host: String get() = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull().orEmpty()
}

private data class SearchSection(
    val query: String,
    val raw: String?,
    val results: List<SearchResult>,
)

/**
 * One card for a whole turn's web searches (a model may issue several calls in a
 * single response). Collapsed: the queries + result favicons. Expanded: the
 * results, grouped per query, grown downward.
 */
@Composable
private fun WebSearchCard(calls: List<Pair<ToolCall, ChatMessage?>>, listState: LazyListState?) {
    val sections = calls.map { (call, result) ->
        SearchSection(
            query = jsonStringArg(call.arguments, "query") ?: "search",
            raw = result?.content,
            results = result?.content?.let(::parseSearchResults).orEmpty(),
        )
    }
    val ready = sections.all { it.raw != null }
    val hosts = sections.flatMap { it.results }.map { it.host }.filter { it.isNotBlank() }.distinct()
    val queryLine = sections.joinToString("   ·   ") { it.query }
    var expanded by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val heightPx = remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableStateOf(0) }
    val maxHeightPx = with(density) { 360.dp.toPx() }

    LaunchedEffect(expanded) {
        val target = if (expanded) minOf(contentHeightPx.toFloat(), maxHeightPx) else 0f
        animate(
            initialValue = heightPx.floatValue,
            targetValue = target,
            animationSpec = tween(durationMillis = 260),
        ) { value, _ ->
            val delta = value - heightPx.floatValue
            heightPx.floatValue = value
            // The list is reverse-laid-out, so a taller item would push everything
            // above it up. Nudge the scroll by the same delta each frame to keep
            // the header pinned and let the results grow downward.
            if (delta != 0f) listState?.dispatchRawDelta(delta)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = ready) { expanded = !expanded },
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
                        text = if (calls.size > 1) "Web search · ${calls.size}" else "Web search",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (queryLine.isNotBlank()) {
                        Text(
                            text = queryLine,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!ready) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (expanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (ready && !expanded && hosts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FaviconRow(hosts)
            }

            if (ready) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { heightPx.floatValue.toDp() })
                        .clipToBounds(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { contentHeightPx = it.height }
                                .padding(top = 6.dp),
                        ) {
                            sections.forEachIndexed { sectionIndex, section ->
                                if (sections.size > 1 && sectionIndex > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                if (sections.size > 1) {
                                    Text(
                                        text = section.query,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                                    )
                                }
                                if (section.results.isEmpty()) {
                                    Text(
                                        text = section.raw.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    section.results.forEachIndexed { index, item ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                modifier = Modifier.padding(vertical = 2.dp),
                                            )
                                        }
                                        SearchResultRow(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A generic collapsible card for non-web-search tool calls. */
@Composable
private fun GenericToolCard(call: ToolCall, result: ChatMessage?, listState: LazyListState?) {
    val label = toolLabel(call.name)
    val summary = toolSummary(call.arguments)
    val resultText = result?.content
    var expanded by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val heightPx = remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableStateOf(0) }
    val maxHeightPx = with(density) { 360.dp.toPx() }

    LaunchedEffect(expanded) {
        val target = if (expanded) minOf(contentHeightPx.toFloat(), maxHeightPx) else 0f
        animate(
            initialValue = heightPx.floatValue,
            targetValue = target,
            animationSpec = tween(durationMillis = 260),
        ) { value, _ ->
            val delta = value - heightPx.floatValue
            heightPx.floatValue = value
            if (delta != 0f) listState?.dispatchRawDelta(delta)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = resultText != null) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = toolIcon(call.name),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (summary.isNotBlank()) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (expanded) 3 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (resultText == null) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (expanded) "Hide" else "Show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (resultText != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(with(density) { heightPx.floatValue.toDp() })
                        .clipToBounds(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { contentHeightPx = it.height }
                                .padding(top = 6.dp),
                        ) {
                            Text(
                                text = resultText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
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

/** Favicons for the collapsed card; preloaded and staggered in. */
@Composable
private fun FaviconRow(hosts: List<String>) {
    val context = LocalContext.current
    LaunchedEffect(hosts) {
        // Prefetch so the icons are ready before they are shown.
        val loader = SingletonImageLoader.get(context)
        hosts.take(6).forEach { host ->
            runCatching {
                loader.enqueue(
                    ImageRequest.Builder(context).data(faviconUrl(host)).build(),
                )
            }
        }
    }
    Row(
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

/** Best-effort extraction of a string argument from a JSON-ish tool-call arguments string. */
private fun jsonStringArg(arguments: String, key: String): String? {
    val match = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").find(arguments) ?: return null
    return match.groupValues[1]
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .ifBlank { null }
}

/** A short human-readable summary of a tool call's arguments. */
private fun toolSummary(arguments: String): String {
    for (key in listOf("query", "location", "expression", "input", "text")) {
        jsonStringArg(arguments, key)?.let { return it }
    }
    return arguments.trim().trim('{', '}', ' ')
}

private fun toolLabel(name: String): String = when (name) {
    WebSearchTool.NAME -> "Web search"
    "get_current_time" -> "Date & time"
    "get_weather" -> "Weather"
    "wikipedia_summary" -> "Wikipedia"
    "calculate" -> "Calculator"
    else -> name
}

private fun toolIcon(name: String) = when (name) {
    WebSearchTool.NAME -> Icons.Default.Search
    "get_current_time" -> Icons.Default.Schedule
    "get_weather" -> Icons.Default.Cloud
    "wikipedia_summary" -> Icons.Default.MenuBook
    "calculate" -> Icons.Default.Calculate
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
