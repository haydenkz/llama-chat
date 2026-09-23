package com.llamacpp.mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.data.tools.FileTool
import com.llamacpp.mobile.data.tools.PythonTool
import com.llamacpp.mobile.data.tools.WebSearchTool
import com.llamacpp.mobile.data.tools.WikipediaTool
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
import com.llamacpp.mobile.domain.model.ToolCall
import com.llamacpp.mobile.ui.appVmFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    container: AppContainer,
    onNavigate: (String) -> Unit,
) {
    val vm: ChatViewModel = viewModel(
        factory = appVmFactory(container) {
            ChatViewModel(
                it.api,
                it.chatRepository,
                it.serverRepository,
                it.settingsRepository,
                it.toolRegistry,
                it.json,
            )
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val hapticsEnabled by container.settingsRepository.hapticsWhileGenerating
        .collectAsStateWithLifecycle(initialValue = true)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showModelPicker by remember { mutableStateOf(false) }
    var showSamplerSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChatMessage?>(null) }
    var pickerAutoOpened by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    // Transient failures (no assistant row to carry them) surface here, with Retry.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Retry",
            withDismissAction = true,
        )
        if (result == SnackbarResult.ActionPerformed) vm.retry()
        vm.dismissError()
    }

    // Re-check reachability whenever the app comes back to the foreground.
    LifecycleResumeEffect(Unit) {
        vm.refreshHealth()
        onPauseOrDispose { }
    }

    // On first setup (no conversations yet) surface the model selector so the
    // choice is obvious — but don't nag on every launch.
    LaunchedEffect(state.loadingModels, state.models.size, state.conversations.size) {
        if (!pickerAutoOpened &&
            !state.loadingModels &&
            state.models.isNotEmpty() &&
            state.conversations.isEmpty()
        ) {
            showModelPicker = true
            pickerAutoOpened = true
        }
    }

    // Always refresh model/load state when the picker opens, so it isn't stale.
    LaunchedEffect(showModelPicker) {
        if (showModelPicker) vm.refreshModels()
    }

    // Subtle tick per streamed chunk while generating.
    val view = LocalView.current
    val lastHaptic = remember { mutableStateOf(0L) }
    LaunchedEffect(state.streamContent.length, state.isStreaming) {
        if (state.isStreaming &&
            state.streamingConversationId == state.selectedId &&
            hapticsEnabled &&
            state.streamContent.isNotEmpty()
        ) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHaptic.value >= HAPTIC_MIN_INTERVAL_MS) {
                lastHaptic.value = now
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ChatDrawer(
                server = state.server,
                serverOnline = state.serverOnline,
                conversations = state.conversations,
                selectedId = state.selectedId,
                workingConversationId = state.streamingConversationId,
                onNewChat = {
                    vm.newConversation()
                    scope.launch { drawerState.close() }
                },
                onSelectConversation = {
                    vm.selectConversation(it)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = vm::deleteConversation,
                onNavigate = {
                    scope.launch { drawerState.close() }
                    onNavigate(it)
                },
            )
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, "Menu")
                        }
                    },
                    title = {
                        Column {
                            Row(
                                modifier = Modifier.clickable { showModelPicker = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.selectedModel?.displayName ?: "Select model",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (state.selectedModel == null) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Icon(
                                    imageVector = Icons.Default.ExpandMore,
                                    contentDescription = "Select model",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.pendingModelIds.isNotEmpty()) {
                                    CircularProgressIndicator(
                                        Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp,
                                    )
                                }
                                Text(
                                    text = state.statusMessage ?: when (state.serverOnline) {
                                        true -> state.server?.name ?: ""
                                        false -> "Server offline"
                                        null -> state.server?.name ?: ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.serverOnline == false) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSamplerSettings = true }) {
                            Icon(Icons.Default.Tune, "Sampling settings")
                        }
                        IconButton(onClick = vm::newConversation) {
                            Icon(Icons.Default.Add, "New chat")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                val generatingHere = state.isStreaming && state.selectedId == state.streamingConversationId
                ChatInputBar(
                    text = draft,
                    onTextChange = { draft = it },
                    generatingHere = generatingHere,
                    generatingElsewhere = state.isStreaming && !generatingHere,
                    modelSelected = !state.activeModelId.isNullOrBlank(),
                    supportsVision = state.selectedModel?.supportsVision == true,
                    onSubmit = { text, images ->
                        vm.send(text, images)
                        draft = ""
                    },
                    onStop = vm::stop,
                )
            },
        ) { padding ->
            MessageList(
                state = state,
                onEdit = { editing = it },
                onRegenerate = vm::regenerate,
                onRetry = vm::retry,
                onSelectModel = { showModelPicker = true },
                onSuggestion = { suggestion ->
                    if (suggestion.prefill) draft = suggestion.prompt else vm.send(suggestion.prompt)
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    if (showModelPicker) {
        ModalModelPicker(
            state = state,
            onDismiss = {
                showModelPicker = false
                vm.clearStatusMessage()
            },
            onSelect = { vm.selectModel(it); showModelPicker = false },
            onRefresh = vm::refreshModels,
        )
    }

    if (showSamplerSettings) {
        SamplerSettingsSheet(
            settings = state.settings,
            customized = state.settingsCustomized,
            onChange = vm::updateSettings,
            onReset = vm::resetSettings,
            onDismiss = { showSamplerSettings = false },
        )
    }

    editing?.let { message ->
        EditMessageDialog(
            original = message,
            onDismiss = { editing = null },
            onConfirm = { text ->
                vm.editMessage(message, text)
                editing = null
            },
        )
    }
}

/** Floor between streaming haptic ticks; faster reads as a continuous buzz. */
private const val HAPTIC_MIN_INTERVAL_MS = 24L

/**
 * A ready-to-send starter prompt. `title`/`subtitle` are what the card shows;
 * `prompt` is sent as-is. `tool` is the tool it relies on, if any, so prompts for
 * switched-off tools are not offered.
 */
private data class Suggestion(
    val title: String,
    val subtitle: String,
    val prompt: String,
    val tool: String? = null,
    /** Fills the composer instead of sending, for prompts the user must finish. */
    val prefill: Boolean = false,
)

private val SUGGESTIONS = listOf(
    Suggestion(
        "Today's tech news", "with sources",
        "Search the web for today's most important technology news and give me a short briefing: " +
            "five bullet points, each with its source.",
        WebSearchTool.NAME,
    ),
    Suggestion(
        "Compare two products", "researched on the web",
        "Search the web and compare the latest iPhone and Pixel flagships: price, camera, battery and " +
            "software support. Finish with which one you'd pick for most people and why.",
        WebSearchTool.NAME,
    ),
    Suggestion(
        "Grow \$10,000", "at 7% for 30 years",
        "Use Python to show how \$10,000 grows at 7% a year for 30 years, compounded monthly, " +
            "with the balance every 5 years in a table.",
        PythonTool.NAME,
    ),
    Suggestion(
        "Split a bill", "tip and tax included",
        "Use Python to split a \$187.40 restaurant bill between 4 people with 13% tax and an 18% tip " +
            "on the pre-tax amount. Show each step.",
        PythonTool.NAME,
    ),
    Suggestion(
        "Weekend packing list", "as a file you can save",
        "Create a markdown checklist file called packing-list.md for a 3-day weekend trip, grouped by " +
            "clothes, toiletries, tech and documents.",
        FileTool.NAME,
    ),
    Suggestion(
        "Weekly meal plan", "with a shopping list file",
        "Plan 5 simple weeknight dinners for two, then create a file called shopping-list.md with every " +
            "ingredient grouped by store section.",
        FileTool.NAME,
    ),
    Suggestion(
        "A random rabbit hole", "from Wikipedia",
        "Pick a surprising historical event, look it up on Wikipedia, and tell me the story in under 200 words.",
        WikipediaTool.NAME,
    ),
    Suggestion(
        "Explain how LLMs work", "in plain language",
        "Explain how a large language model like you generates an answer, in plain language, " +
            "in under 200 words, with one everyday analogy.",
    ),
    Suggestion(
        "Debug my code", "paste it after this",
        "Here's some code with a bug. Find the bug, explain it in one sentence, and show the fix.\n\n",
        prefill = true,
    ),
)

/** Four suggestions whose tools are enabled, favouring a mix of different tools. */
private fun pickSuggestions(disabledTools: Set<String>): List<Suggestion> {
    val available = SUGGESTIONS.filter { it.tool == null || it.tool !in disabledTools }.shuffled()
    val oneEach = available.distinctBy { it.tool }
    return (oneEach + (available - oneEach.toSet())).take(4)
}

/** A user bubble or a grouped assistant turn. */
private sealed interface ChatItem {
    data class User(val message: ChatMessage) : ChatItem
    data class Assistant(val turn: AssistantTurn) : ChatItem
}

/**
 * Groups consecutive assistant/tool messages into a single turn, keyed by the
 * user message that started it, so thinking + tool calls + the answer render as
 * one unit instead of separate bubbles.
 */
private fun groupIntoTurns(messages: List<ChatMessage>): List<ChatItem> {
    val out = ArrayList<ChatItem>()
    var turnKey = -1L
    var buffer = ArrayList<ChatMessage>()

    fun flush() {
        if (buffer.isNotEmpty()) {
            out.add(ChatItem.Assistant(AssistantTurn(id = turnKey, messages = buffer.toList())))
            buffer = ArrayList()
        }
    }

    messages.forEach { message ->
        when (message.role) {
            ChatRole.System -> Unit
            ChatRole.User -> {
                flush()
                turnKey = message.id
                out.add(ChatItem.User(message))
            }
            else -> buffer.add(message)
        }
    }
    flush()
    return out
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageList(
    state: ChatUiState,
    onEdit: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    onSelectModel: () -> Unit,
    onSuggestion: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Only the conversation being generated shows streaming content.
    val streamingHere = state.isStreaming && state.selectedId == state.streamingConversationId

    // Keyed on real ids only: a legacy id-less tool row must not shadow another chip's output.
    val toolResults = remember(state.messages) {
        state.messages
            .filter { it.role == ChatRole.Tool && !it.toolCallId.isNullOrBlank() }
            .associateBy { it.toolCallId!! }
    }

    val displayItems: List<ChatItem> = remember(state.messages, streamingHere) {
        val grouped = groupIntoTurns(state.messages)
        if (streamingHere && grouped.lastOrNull() !is ChatItem.Assistant) {
            val lastUserId = state.messages.lastOrNull { it.role == ChatRole.User }?.id ?: -1L
            grouped + ChatItem.Assistant(AssistantTurn(id = lastUserId, messages = emptyList()))
        } else {
            grouped
        }
    }
    val lastItem = displayItems.lastOrNull()

    val copyMessage: (ChatMessage) -> Unit = { message ->
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
            ?.setPrimaryClip(ClipData.newPlainText("message", message.content))
    }

    // Auto-follow keeps the newest content in view. The list is top-anchored, so
    // once the user drags away, growing text below no longer moves what they are
    // reading; settling back at the bottom (or the jump button) resumes following.
    var following by remember { mutableStateOf(true) }
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(state.selectedId) { following = true }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling && touching) {
                following = false
            } else if (!scrolling && !touching) {
                following = !listState.canScrollForward
            }
        }
    }
    // A newly sent message always brings the list back down.
    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.lastOrNull()?.role == ChatRole.User) following = true
    }
    // Pin to the bottom when content arrives, applied in the same frame's layout as
    // the change so following never lags behind the text. Only new content pins:
    // expanding a finished turn's work log must not drag the list.
    val bottomIndex = displayItems.size + if (state.serverOnline == false) 1 else 0
    val contentKey = listOf(
        state.selectedId, state.messages.size, state.isStreaming, state.streamContent.length,
        state.streamReasoning.length, state.streamToolCalls.size, bottomIndex,
    )
    val pinnedKey = remember { arrayOfNulls<Any>(1) }
    SideEffect {
        if (pinnedKey[0] != contentKey) {
            pinnedKey[0] = contentKey
            if (following && !touching && !listState.isScrollInProgress) {
                listState.requestScrollToItem(bottomIndex)
            }
        }
    }

    if (state.messages.isEmpty() && !state.isStreaming) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (state.selectedModel == null) "Choose a model to begin" else "Start a conversation",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (state.models.isEmpty() && !state.loadingModels) {
                        "No models were reported by the server."
                    } else {
                        "Send a message to ${state.selectedModel?.displayName ?: "your model"}."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (state.selectedModel == null && state.models.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSelectModel) { Text("Select a model") }
                }
                Spacer(Modifier.height(24.dp))
                val suggestions = remember(state.selectedId, state.disabledTools) {
                    pickSuggestions(state.disabledTools)
                }
                val enabled = state.selectedModel != null && !state.isStreaming
                suggestions.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(IntrinsicSize.Min),
                    ) {
                        row.forEach { suggestion ->
                            SuggestionCard(
                                suggestion = suggestion,
                                enabled = enabled,
                                onClick = { onSuggestion(suggestion) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        return
    }

    Box(modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // Observe (never consume) touches so a drag can be told apart from
                    // our own scrolling.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touching = true
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        touching = false
                        if (!listState.isScrollInProgress) following = !listState.canScrollForward
                    }
                },
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(displayItems, key = { item ->
                when (item) {
                    is ChatItem.User -> "u${item.message.id}"
                    is ChatItem.Assistant -> "t${item.turn.id}"
                }
            }) { item ->
                when (item) {
                    is ChatItem.User -> UserBubble(
                        message = item.message,
                        onCopy = { copyMessage(item.message) },
                        onEdit = { onEdit(item.message) },
                    )

                    is ChatItem.Assistant -> {
                        val isLast = item === lastItem
                        AssistantTurnView(
                            turn = item.turn,
                            toolResults = toolResults,
                            isStreaming = isLast && streamingHere,
                            streamingContent = if (isLast && streamingHere) state.streamContent else "",
                            streamingReasoning = if (isLast && streamingHere) state.streamReasoning else "",
                            streamingToolCalls = if (isLast && streamingHere) state.streamToolCalls else emptyList(),
                            streamingThinkingMs = if (isLast && streamingHere) state.streamThinkingMs else 0L,
                            streamingThinkingSince = if (isLast && streamingHere) state.streamThinkingSince else null,
                            canRegenerate = isLast && !state.isStreaming,
                            onCopy = copyMessage,
                            onRegenerate = onRegenerate,
                            onRetry = onRetry,
                        )
                    }
                }
            }

            if (state.serverOnline == false) {
                item(key = "offline-banner") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            text = "Cannot reach ${state.server?.normalizedBaseUrl}. Check the server address in Servers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }

            // Zero-height anchor: scrolling to it pins the end of the list to the bottom.
            item(key = "bottom-anchor") { Spacer(Modifier.fillMaxWidth()) }
        }

        // Jump back to the newest content once the user has scrolled away from it.
        AnimatedVisibility(
            visible = !following && listState.canScrollForward,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            SmallFloatingActionButton(
                onClick = {
                    following = true
                    scope.launch { listState.animateScrollToItem(bottomIndex) }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Icon(Icons.Default.ArrowDownward, "Jump to latest")
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: Suggestion,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = suggestion.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModalModelPicker(
    state: ChatUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        ModelPickerList(
            models = state.models,
            selectedId = state.activeModelId,
            loading = state.loadingModels,
            error = state.modelsError,
            onSelect = onSelect,
            onRefresh = onRefresh,
            pendingIds = state.pendingModelIds,
            hiddenIds = state.server?.hiddenModels?.toSet().orEmpty(),
        )
    }
}
