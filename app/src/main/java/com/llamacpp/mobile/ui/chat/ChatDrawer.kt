package com.llamacpp.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.domain.model.Conversation
import com.llamacpp.mobile.domain.model.ServerConfig
import com.llamacpp.mobile.ui.navigation.Routes

@Composable
fun ChatDrawer(
    server: ServerConfig?,
    serverOnline: Boolean?,
    conversations: List<Conversation>,
    selectedId: String?,
    onNewChat: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onNavigate: (String) -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "LlamaChat",
                style = MaterialTheme.typography.titleLarge,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .background(
                            color = when (serverOnline) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = server?.name ?: "No server",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        NavigationDrawerItem(
            label = { Text("New chat") },
            icon = { Icon(Icons.Default.Add, null) },
            selected = false,
            onClick = onNewChat,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        Text(
            text = "Conversations",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(conversations, key = { it.id }) { conversation ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = conversation.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    selected = conversation.id == selectedId,
                    onClick = { onSelectConversation(conversation.id) },
                    badge = {
                        IconButton(onClick = { onDeleteConversation(conversation.id) }) {
                            Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        NavigationDrawerItem(
            label = { Text("Completion") },
            icon = { Icon(Icons.Default.Tag, null) },
            selected = false,
            onClick = { onNavigate(Routes.COMPLETION) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Server info") },
            icon = { Icon(Icons.Default.Memory, null) },
            selected = false,
            onClick = { onNavigate(Routes.SERVER_INFO) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, null) },
            selected = false,
            onClick = { onNavigate(Routes.SETTINGS) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.padding(8.dp))
    }
}
