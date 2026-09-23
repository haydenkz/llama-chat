package com.llamacpp.mobile.ui.chat

import android.content.Intent
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.unit.IntSize
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A run of assistant/tool messages between two user messages. */
@Immutable
data class AssistantTurn(
    val id: Long,
    val messages: List<ChatMessage>,
)

@Composable
fun UserBubble(
    message: ChatMessage,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Spacer(Modifier.weight(1f))
        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(onClick = {}, onLongClick = { menu = true }),
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
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = { menu = false; onCopy() },
                )
                if (message.role == ChatRole.User) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { menu = false; onEdit() },
                    )
                }
            }
        }
    }
}

/** One entry in a turn's work log: something the model thought or a tool it called. */
private sealed interface WorkStep {
    data class Thought(val text: String) : WorkStep
    data class Tool(val call: ToolCall) : WorkStep
}

/**
 * Renders an assistant turn: a collapsible **work** section (live status while
 * the model thinks and calls tools, expanding to an ordered log of its thoughts
 * and tool calls), the **answer** (streams live), then a **Files** section.
 */
@Composable
fun AssistantTurnView(
    turn: AssistantTurn,
    toolResults: Map<String, ChatMessage>,
    isStreaming: Boolean,
    streamingContent: String,
    streamingReasoning: String,
    /** Tool calls still being written by the model (streaming only). */
    streamingToolCalls: List<ToolCall>,
    /** Thinking time accumulated so far this turn (streaming only). */
    streamingThinkingMs: Long,
    /** Start of the live thinking span, or null while answer text streams. */
    streamingThinkingSince: Long?,
    canRegenerate: Boolean,
    onCopy: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only assistant text can be the answer: a tool result row also has content and no calls.
    val finalAnswer = remember(turn.messages) {
        turn.messages.lastOrNull { it.role == ChatRole.Assistant && it.content.isNotBlank() && it.toolCalls.isEmpty() }
    }
    val fileCalls = remember(turn.messages) {
        turn.messages.flatMap { it.toolCalls }.filter { it.name == FileTool.NAME }
    }
    val error = remember(turn.messages) { turn.messages.firstNotNullOfOrNull { it.error } }

    // The work log in the order it happened: each pass's reasoning and narration,
    // then the tools it called; the live pass goes last.
    val steps = remember(turn.messages, streamingReasoning, streamingToolCalls) {
        buildList {
            turn.messages.filter { it.role == ChatRole.Assistant }.forEach { message ->
                val thought = listOf(message.reasoning, message.content.takeIf { message !== finalAnswer }.orEmpty())
                    .map(::tidyThought)
                    .filter(String::isNotBlank)
                    .joinToString("\n\n")
                if (thought.isNotBlank()) add(WorkStep.Thought(thought))
                message.toolCalls.forEach { add(WorkStep.Tool(it)) }
            }
            tidyThought(streamingReasoning).takeIf(String::isNotBlank)?.let { add(WorkStep.Thought(it)) }
            streamingToolCalls.forEach { add(WorkStep.Tool(it)) }
        }
    }
    val toolCount = steps.count { it is WorkStep.Tool }
    val persistedThinkingMs = remember(turn.messages) { turn.messages.sumOf { it.thinkingMs ?: 0L } }
    // "Working" = the model is still going and hasn't produced answer text yet.
    val working = isStreaming && streamingContent.isBlank() && finalAnswer == null

    // Live clock, ticking only while a thinking span is open, so the counter shown
    // while working is the same number the persisted "Thought for" ends on.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isStreaming, streamingThinkingSince) {
        while (isStreaming && streamingThinkingSince != null) {
            now = System.currentTimeMillis()
            delay(250L)
        }
    }
    val thinkingMs = if (isStreaming) {
        streamingThinkingMs + (streamingThinkingSince?.let { (now - it).coerceAtLeast(0L) } ?: 0L)
    } else {
        persistedThinkingMs
    }

    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // "Thinking" only appears once there is reasoning or a tool call; a reply
        // that is simply waiting for its first token gets a neutral indicator.
        // The dots and the header share one height, so swapping them never moves
        // the answer; every other size change is animated inside the work section.
        Column(Modifier.fillMaxWidth()) {
            if (steps.isEmpty() && working) {
                Box(Modifier.height(WORK_HEADER_HEIGHT), contentAlignment = Alignment.CenterStart) { PendingDots() }
            } else if (steps.isNotEmpty()) {
                WorkSection(
                    steps = steps,
                    toolResults = toolResults,
                    working = working,
                    turnActive = isStreaming,
                    durationSeconds = thinkingMs / 1000L,
                    toolCount = toolCount,
                )
            }
        }

        // The live buffer wins while streaming; the persisted answer takes over in
        // the same frame the buffer is cleared, so there is no gap between them.
        val answer = if (isStreaming && streamingContent.isNotEmpty()) streamingContent else finalAnswer?.content
        if (answer != null) {
            StreamingMarkdownText(content = answer, modifier = Modifier.fillMaxWidth())
        }

        // Files only appear once the model has finished responding.
        if (!isStreaming && fileCalls.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            fileCalls.forEach { call ->
                FileRow(call = call, result = toolResults[call.id])
            }
        }

        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (!isStreaming) {
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
        }

        if (!isStreaming && finalAnswer != null) {
            AnswerActions(
                message = finalAnswer,
                canRegenerate = canRegenerate,
                onCopy = { onCopy(finalAnswer) },
                onRegenerate = onRegenerate,
            )
        }
    }
}

/** Collapses the blank-line runs models leave between narration chunks. */
private fun tidyThought(text: String): String = text.trim().replace(BLANK_LINE_RUN, "\n\n")

private val BLANK_LINE_RUN = Regex("""\n[ \t]*(\n[ \t]*)+""")

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

/**
 * The turn's work log. Collapsed it is one status line — shimmering with the
 * current activity while working, "Thought for Xs · N tools" after — plus a
 * one-line preview of the latest step while working. Expanded (manual toggle
 * only, it never opens or closes on its own) it lists every thought and tool
 * call in order.
 */
@Composable
private fun WorkSection(
    steps: List<WorkStep>,
    toolResults: Map<String, ChatMessage>,
    working: Boolean,
    turnActive: Boolean,
    durationSeconds: Long,
    toolCount: Int,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = steps.isNotEmpty()
    val latest = steps.lastOrNull()
    val runningTool = (latest as? WorkStep.Tool)?.call?.takeIf { turnActive && toolResults[it.id] == null }

    val label = when {
        !working -> workSummary(durationSeconds, toolCount)
        runningTool != null -> toolLabel(runningTool.name, running = true)
        else -> "Thinking"
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        // Fixed-height header: the label crossfades, the timer and chevron hold
        // their place, so cycling through steps never shifts anything.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = WORK_HEADER_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = canExpand) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedContent(
                targetState = label to working,
                transitionSpec = {
                    fadeIn(tween(STEP_FADE_MS)) togetherWith fadeOut(tween(STEP_FADE_MS)) using SizeTransform(clip = false)
                },
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.weight(1f),
                label = "workLabel",
            ) { (text, isWorking) ->
                if (isWorking) {
                    ShimmerText(text = text)
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (working) {
                Text(
                    text = "${durationSeconds}s",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canExpand) {
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "workChevron")
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide work" else "Show work",
                    modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // While working, a fixed-height slot shows the current step: the raw thought
        // pinned to its newest line, or the running tool's input. Steps crossfade
        // in place; the slot's size never changes between them.
        // Starts hidden so it slides in when thinking begins, on the same timing as
        // everything else in this section.
        val slotVisible = remember { MutableTransitionState(false) }
        slotVisible.targetState = working && !expanded
        AnimatedVisibility(
            visibleState = slotVisible,
            enter = expandVertically(workSizeSpec(), expandFrom = Alignment.Top) + fadeIn(tween(STEP_FADE_MS)),
            exit = shrinkVertically(workSizeSpec(), shrinkTowards = Alignment.Top) + fadeOut(tween(STEP_FADE_MS)),
        ) {
            val lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                .takeIf { it.isSpecified } ?: MaterialTheme.typography.bodySmall.fontSize * 1.4f
            val slotHeight = with(LocalDensity.current) { (lineHeight * LIVE_SLOT_LINES).toDp() }
            AnimatedContent(
                targetState = steps.lastIndex,
                transitionSpec = {
                    fadeIn(tween(STEP_FADE_MS)) togetherWith fadeOut(tween(STEP_FADE_MS)) using SizeTransform(clip = true)
                },
                modifier = Modifier.fillMaxWidth().height(slotHeight).padding(bottom = 2.dp),
                label = "workStep",
            ) { index ->
                when (val step = steps.getOrNull(index)) {
                    is WorkStep.Thought -> LiveThought(step.text)
                    is WorkStep.Tool -> LiveTool(step.call, running = turnActive && toolResults[step.call.id] == null)
                    null -> Spacer(Modifier.fillMaxSize())
                }
            }
        }

        AnimatedVisibility(
            visible = expanded && canExpand,
            enter = expandVertically(workSizeSpec(), expandFrom = Alignment.Top) + fadeIn(tween(STEP_FADE_MS)),
            exit = shrinkVertically(workSizeSpec(), shrinkTowards = Alignment.Top) + fadeOut(tween(STEP_FADE_MS)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp)) {
                steps.forEach { step ->
                    when (step) {
                        is WorkStep.Thought -> ThoughtBlock(step.text)
                        is WorkStep.Tool -> ToolChip(
                            call = step.call,
                            result = toolResults[step.call.id],
                            turnActive = turnActive,
                        )
                    }
                }
            }
        }
    }
}

/** Three softly pulsing dots: the model is working but has produced nothing yet. */
@Composable
private fun PendingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pendingDots")
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0.25f at index * 150
                        1f at index * 150 + 300
                        0.25f at index * 150 + 600
                    },
                ),
                label = "pendingDot$index",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(color, CircleShape),
            )
        }
    }
}

/** The current thought, verbatim, filling the live slot and pinned to its newest line. */
@Composable
private fun LiveThought(text: String) {
    val scroll = rememberScrollState()
    LaunchedEffect(scroll) { snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) } }
    val fade = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().verticalScroll(scroll, enabled = false)) {
            ThoughtBlock(text, bottomPadding = 0.dp)
        }
        // Older lines fade out at the top edge instead of being cut.
        if (scroll.value > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(Brush.verticalGradient(listOf(fade, fade.copy(alpha = 0f)))),
            )
        }
    }
}

/** The running (or just-finished) tool's input, in the same slot as a thought. */
@Composable
private fun LiveTool(call: ToolCall, running: Boolean) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(top = 1.dp).size(14.dp), contentAlignment = Alignment.Center) {
            if (running) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            } else {
                Icon(
                    imageVector = toolIcon(call.name),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = toolSummary(call.arguments),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = LIVE_SLOT_LINES,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val WORK_HEADER_HEIGHT = 32.dp
private const val LIVE_SLOT_LINES = 3
private const val STEP_FADE_MS = 220

/**
 * The one size animation for the work section (live slot, expanded log). A single
 * tween drives each resize, and the answer below simply follows the layout, so
 * it moves in lockstep instead of trailing behind.
 */
private fun workSizeSpec() = tween<IntSize>(durationMillis = 260, easing = FastOutSlowInEasing)

/** A thought in the work log: quiet italic text behind a thin rule. */
@Composable
private fun ThoughtBlock(text: String, bottomPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    val rule = MaterialTheme.colorScheme.outlineVariant
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .drawBehind {
                drawRect(color = rule, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
            }
            .padding(start = 12.dp),
    )
}

private fun workSummary(seconds: Long, tools: Int): String {
    val toolText = when (tools) {
        0 -> null
        1 -> "1 tool"
        else -> "$tools tools"
    }
    val thought = when {
        seconds < 1L -> if (toolText != null) null else "Thought briefly"
        seconds < 60L -> "Thought for ${seconds}s"
        else -> "Thought for ${seconds / 60}m ${seconds % 60}s"
    }
    return when {
        thought != null && toolText != null -> "$thought · $toolText"
        toolText != null -> "Used $toolText"
        else -> thought.orEmpty()
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

/**
 * A compact, collapsible tool chip. Web search keeps its favicons; other tools show
 * a one-line input and the output.
 */
@Composable
private fun ToolChip(call: ToolCall, result: ChatMessage?, turnActive: Boolean) {
    val summary = toolSummary(call.arguments)
    // A call that never got a result after the turn ended is shown as such, not as running.
    val resultText = result?.content ?: if (turnActive) null else "(not run)"
    val running = resultText == null
    val label = toolLabel(call.name, running)
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

private val SUMMARY_KEYS = listOf("query", "location", "expression", "code", "filename", "title", "input", "text")

/**
 * A short human-readable summary of a tool call's arguments. Works on partial
 * JSON too, so a call the model is still writing already shows its input.
 */
private fun toolSummary(arguments: String): String {
    for (key in SUMMARY_KEYS) {
        val value = jsonArg(arguments, key) ?: partialStringArg(arguments, key)
        val firstLine = value?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isNotBlank()) return firstLine.take(80)
    }
    if (arguments.trimStart().startsWith("{")) return ""
    return arguments.trim().take(80)
}

/** A string value that may still be unterminated: `{"query": "weather in Par`. */
private fun partialStringArg(arguments: String, key: String): String? =
    Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)")
        .find(arguments)
        ?.groupValues?.get(1)
        ?.replace("\\n", "\n")
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")

/** Present tense while the tool runs, past tense once it has a result. */
private fun toolLabel(name: String, running: Boolean = false): String = when (name) {
    WebSearchTool.NAME -> if (running) "Searching the web" else "Searched the web"
    "get_current_time" -> if (running) "Checking the time" else "Checked the time"
    "get_weather" -> if (running) "Checking the weather" else "Checked the weather"
    "wikipedia_summary" -> if (running) "Looking up Wikipedia" else "Looked up Wikipedia"
    "calculate" -> if (running) "Calculating" else "Calculated"
    "run_python" -> if (running) "Running Python" else "Ran Python"
    FileTool.NAME -> if (running) "Writing a file" else "Created a file"
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

/**
 * Renders a `data:` image. Decoding happens off the main thread and is bounded to
 * ~1024 px on the longest edge so full-size photos stored by older versions cannot
 * stall a frame or exhaust memory.
 */
@Composable
fun DataUriImage(dataUri: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, dataUri) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val encoded = dataUri.substringAfter("base64,", "")
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_IMAGE_EDGE_PX)
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** Longest edge, in pixels, that attached and displayed images are bounded to. */
internal const val MAX_IMAGE_EDGE_PX = 1024

/** Power-of-two `inSampleSize` that brings the longest edge to at most `maxEdge`. */
internal fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    var longest = maxOf(width, height)
    while (longest / 2 >= maxEdge) {
        longest /= 2
        sample *= 2
    }
    return sample
}
