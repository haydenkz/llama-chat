package com.llamacpp.mobile.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.domain.model.ChatMessage
import com.llamacpp.mobile.domain.model.ChatRole
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

    // ChatGPT-style subtle tick per streamed chunk while generating.
    val view = LocalView.current
    val lastHaptic = remember { mutableStateOf(0L) }
    LaunchedEffect(state.streamContent.length, state.isStreaming) {
        if (state.isStreaming && hapticsEnabled && state.streamContent.isNotEmpty()) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHaptic.value >= 24L) {
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
            bottomBar = {
                ChatInputBar(
                    isStreaming = state.isStreaming,
                    modelSelected = !state.activeModelId.isNullOrBlank(),
                    supportsVision = state.selectedModel?.supportsVision == true,
                    onSend = { text, images -> vm.send(text, images) },
                    onStop = vm::stop,
                )
            },
        ) { padding ->
            MessageList(
                state = state,
                onEdit = { editing = it },
                onRegenerate = vm::regenerate,
                onSelectModel = { showModelPicker = true },
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
            onChange = vm::updateSettings,
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

@Composable
private fun MessageList(
    state: ChatUiState,
    onEdit: (ChatMessage) -> Unit,
    onRegenerate: () -> Unit,
    onSelectModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val toolResults = remember(state.messages) {
        state.messages.filter { it.role == ChatRole.Tool }.associateBy { it.toolCallId.orEmpty() }
    }

    val displayItems: List<ChatItem> = remember(state.messages, state.isStreaming) {
        val grouped = groupIntoTurns(state.messages)
        if (state.isStreaming && grouped.lastOrNull() !is ChatItem.Assistant) {
            val lastUserId = state.messages.lastOrNull { it.role == ChatRole.User }?.id ?: -1L
            grouped + ChatItem.Assistant(AssistantTurn(id = lastUserId, messages = emptyList()))
        } else {
            grouped
        }
    }
    val reversed = remember(displayItems) { displayItems.asReversed() }
    val lastItem = displayItems.lastOrNull()

    val copyMessage: (ChatMessage) -> Unit = { message ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
    }

    var autoFollow by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var wasScrolling = false
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                wasScrolling = true
            } else if (wasScrolling) {
                wasScrolling = false
                autoFollow = listState.firstVisibleItemIndex == 0
            }
        }
    }

    LaunchedEffect(state.messages.size, state.isStreaming) {
        if (state.isStreaming || state.messages.lastOrNull()?.role == ChatRole.User) {
            autoFollow = true
            listState.animateScrollToItem(0)
        }
    }

    // `reverseLayout` keeps the newest content pinned on its own; we only nudge
    // once when generation finishes (transient bubble → persisted message).
    LaunchedEffect(state.isStreaming) {
        if (!state.isStreaming && autoFollow) {
            listState.requestScrollToItem(0)
        }
    }

    if (state.conversation == null && state.messages.isEmpty() && !state.isStreaming) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(reversed, key = { item ->
            when (item) {
                is ChatItem.User -> "u${item.message.id}"
                is ChatItem.Assistant -> "t${item.turn.id}"
            }
        }) { item ->
            when (item) {
                is ChatItem.User -> UserBubble(item.message)

                is ChatItem.Assistant -> {
                    val isLast = item === lastItem
                    AssistantTurnView(
                        turn = item.turn,
                        toolResults = toolResults,
                        isStreaming = isLast && state.isStreaming,
                        streamingContent = if (isLast && state.isStreaming) state.streamContent else "",
                        streamingReasoning = if (isLast && state.isStreaming) state.streamReasoning else "",
                        listState = listState,
                        canRegenerate = isLast && !state.isStreaming,
                        onCopy = copyMessage,
                        onRegenerate = onRegenerate,
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
            onSelect = onSelect,
            onRefresh = onRefresh,
            pendingIds = state.pendingModelIds,
        )
    }
}
