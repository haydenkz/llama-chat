package com.llamacpp.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llamacpp.mobile.domain.model.LlamaModel
import com.llamacpp.mobile.ui.components.InfoChip
import com.llamacpp.mobile.ui.util.formatBytes
import com.llamacpp.mobile.ui.util.formatParams
import com.llamacpp.mobile.ui.util.formatTokens

/**
 * Model selector inspired by the llama.cpp web UI. Selecting a model loads it
 * automatically (the router evicts as needed), so there are no manual
 * load/unload controls — the row simply reflects progress.
 */
@Composable
fun ModelPickerList(
    models: List<LlamaModel>,
    selectedId: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    pendingIds: Set<String> = emptySet(),
    hiddenIds: Set<String> = emptySet(),
) {
    val visible = remember(models, hiddenIds) { models.filterNot { it.id in hiddenIds } }
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Models",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh models")
            }
        }

        when {
            loading && visible.isEmpty() -> {
                Row(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Loading models…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            visible.isEmpty() -> {
                Text(
                    text = if (models.isEmpty()) {
                        "No models available."
                    } else {
                        "All models are hidden. Manage them in Servers → edit this server."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            }

            else -> {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(visible, key = { it.id }) { model ->
                        ModelRow(
                            model = model,
                            selected = model.id == selectedId,
                            pending = model.id in pendingIds,
                            onSelect = { onSelect(model.id) },
                        )
                    }
                }
            }
        }

        if (hiddenIds.isNotEmpty() && visible.isNotEmpty()) {
            Text(
                text = "${hiddenIds.size} hidden · manage in Servers",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelRow(
    model: LlamaModel,
    selected: Boolean,
    pending: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(loaded = model.isLoaded, loading = model.isLoading || pending)

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp).size(18.dp),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                InfoChip(
                    when {
                        pending -> "loading…"
                        model.isLoaded -> "loaded"
                        model.isLoading -> "loading"
                        else -> model.status ?: "unloaded"
                    },
                )
                model.meta?.ftype?.let { InfoChip(it) }
                model.meta?.sizeBytes?.let { InfoChip(formatBytes(it)) }
                model.meta?.nParams?.let { InfoChip("${formatParams(it)} params") }
                model.meta?.nCtx?.let { InfoChip("ctx ${formatTokens(it)}") }
                if (model.supportsVision) InfoChip("vision")
            }
        }

        if (pending || model.isLoading) {
            CircularProgressIndicator(
                Modifier.padding(start = 12.dp).size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun StatusDot(loaded: Boolean, loading: Boolean) {
    val color = when {
        loading -> MaterialTheme.colorScheme.tertiary
        loaded -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Spacer(
        Modifier
            .size(10.dp)
            .background(color = color, shape = CircleShape),
    )
}
