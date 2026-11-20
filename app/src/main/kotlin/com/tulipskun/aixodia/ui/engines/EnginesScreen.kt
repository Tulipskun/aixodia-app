package com.tulipskun.aixodia.ui.engines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.data.remote.EngineDirectory
import kotlinx.coroutines.launch

/**
 * The first screen: the engines the gateway knows about.
 *
 * The web console opens on its engines list and opens a chat (or a shell) from
 * the machine's card. The phone now does the same: no engine is implied by a
 * bare address, and the list is the place to choose one. Nothing here writes a
 * selection — a card is probed first, and only a healthy engine is handed to
 * the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnginesScreen(
    engines: EngineDirectory,
    onOpen: (EngineEntry, EngineDirectory.Probe, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val snapshot = remember { mutableStateOf(EngineDirectory.Snapshot(emptyList(), "", 0)) }
    val loading = remember { mutableStateOf(true) }
    val message = remember { mutableStateOf("") }

    LaunchedEffect(engines) {
        loading.value = true
        snapshot.value = engines.refresh()
        loading.value = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Engines", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${snapshot.value.engines.size} engines",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            loading.value = true
                            snapshot.value = engines.refresh()
                            loading.value = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh engine list")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                loading.value && snapshot.value.engines.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                snapshot.value.engines.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "No engines in the registry. Start an engine, then refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            scope.launch {
                                loading.value = true
                                snapshot.value = engines.refresh()
                                loading.value = false
                            }
                        }) { Text("Refresh") }
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        item {
                            Text(
                                if (snapshot.value.fetchedAt > 0) "Updated just now"
                                else "Showing the last saved list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(snapshot.value.engines, key = { it.id }) { engine ->
                            EngineCard(
                                engine = engine,
                                active = engine.id == snapshot.value.activeId,
                                busy = message.value == engine.id,
                                onOpen = { pane ->
                                    message.value = engine.id
                                    scope.launch {
                                        val probe = engines.probe(engine.url)
                                        message.value = ""
                                        if (probe.ok) {
                                            engines.select(engine.id)
                                            onOpen(engine, probe, pane)
                                        } else {
                                            message.value = "Cannot reach ${engine.name}: ${probe.error.ifBlank { "not an engine" }}"
                                        }
                                    }
                                },
                            )
                        }
                        if (message.value.startsWith("Cannot reach")) {
                            item {
                                Text(
                                    message.value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineCard(
    engine: EngineEntry,
    active: Boolean,
    busy: Boolean,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (engine.online) "●" else "○",
                    color = if (engine.online) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(engine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            engine.subtitle.ifBlank { null },
                            engine.tag.ifBlank { null },
                            engine.url,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (active) {
                    Text("ACTIVE", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
            if (engine.version.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(engine.version, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onOpen("chat") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (busy) "Checking…" else "AI") }
                OutlinedButton(
                    onClick = { onOpen("terminal") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Terminal") }
                OutlinedButton(
                    onClick = { onOpen("screen") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) { Text("Screen") }
            }
        }
    }
}
