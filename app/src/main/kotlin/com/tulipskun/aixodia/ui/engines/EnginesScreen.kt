package com.tulipskun.aixodia.ui.engines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.data.remote.EngineDeleteAnswer
import com.tulipskun.aixodia.data.remote.EngineDirectory
import com.tulipskun.aixodia.data.remote.Health
import com.tulipskun.aixodia.data.remote.HistoryApi
import com.tulipskun.aixodia.readings.EngineReadings
import com.tulipskun.aixodia.readings.EngineReadingsStore
import com.tulipskun.aixodia.readings.MetricShape
import com.tulipskun.aixodia.ui.display.ConsoleBar
import com.tulipskun.aixodia.ui.display.ScreenHead
import com.tulipskun.aixodia.ui.nav.Place
import com.tulipskun.aixodia.ui.theme.aixodia
import com.tulipskun.aixodia.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the cards re-read an engine's `/healthz`. */
private const val HEALTH_MS = 2_000L

/** The blur's strength while the menu is up. */
private const val BLUR_DP = 2f

/** Long enough for the overlay's exit animation to finish before it is dropped. */
private const val EXIT_HOLD_MS = 160L

/** The same bound the gateway keeps on a name, so the field cannot hold what it would drop. */
private const val ALIAS_MAX = 40

/**
 * The first screen: the engines the gateway knows about.
 *
 * It is a screen of the same console the web draws. The bar is [ConsoleBar], the
 * heading is [ScreenHead], and a card has the console's three bands — who, what
 * it reports, what you can do — separated by hairlines rather than shadows. The
 * phone only has three panes behind a card (chat, terminal, screen), so it draws
 * those three as equal peers the way the console draws its actions; the web's
 * fourth, unwired verb is not drawn here because the phone has nothing behind it.
 *
 * Nothing here writes a selection on its own: a card is probed first, and only
 * a healthy engine is handed to the pane that opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnginesScreen(
    engines: EngineDirectory,
    readings: EngineReadingsStore,
    history: HistoryApi,
    onOpen: (EngineEntry, EngineDirectory.Probe, Place) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDroidime: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snapshot = remember { mutableStateOf(EngineDirectory.Snapshot(emptyList(), "", 0)) }
    val loading = remember { mutableStateOf(true) }
    val message = remember { mutableStateOf("") }
    // The card whose More was pressed, the row of the sheet that is working, and
    // the one line that says how the last one ended. All three live here rather
    // than in the sheet: a result has to outlive the sheet being dismissed.
    val menuEngine = remember { mutableStateOf<EngineEntry?>(null) }
    val menuOpen = remember { MutableTransitionState(false) }
    val menuWorking = remember { mutableStateOf("") }
    val menuNote = remember { mutableStateOf("") }
    // The two questions the menu asks before it writes: a new name, and whether
    // to forget the machine. Held here, beside the menu's own state, because the
    // dialogs outlive the tiles' exit animation.
    val renameEngine = remember { mutableStateOf<EngineEntry?>(null) }
    val renameText = remember { mutableStateOf("") }
    val deleteEngine = remember { mutableStateOf<EngineEntry?>(null) }

    fun openMenu(engine: EngineEntry) {
        menuNote.value = ""
        menuWorking.value = ""
        menuEngine.value = engine
        menuOpen.targetState = true
    }

    // The overlay stays composed while it fades out: dropping the engine at the
    // tap would take the tiles with it in one frame. The wait is the exit's own
    // length, so nothing is left behind and nothing is cut short.
    fun closeMenu() {
        menuOpen.targetState = false
        scope.launch {
            delay(EXIT_HOLD_MS)
            if (!menuOpen.targetState) menuEngine.value = null
        }
    }
    // Back closes what is drawn over the list before it leaves the app: the
    // tiles first, then the drawer. Without this, pressing back with a menu up
    // closed the whole app and the menu went with it — the one screen a reader
    // is most likely to be surprised by, because nothing of the app is left
    // showing when it happens.
    BackHandler(enabled = menuOpen.targetState) { closeMenu() }
    BackHandler(enabled = drawer.isOpen || drawer.targetValue != DrawerValue.Closed) {
        scope.launch { drawer.close() }
    }

    // What each card's strip draws. The windows belong to the app, not to this
    // screen: leaving the list for a thread and coming back must not empty them.
    val windows by readings.byEngine.collectAsState()
    // What the phone itself last measured about each machine. Absent until the
    // first answer arrives, which is when the registry's lease stops being the
    // word.
    val live = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun reload() {
        loading.value = true
        snapshot.value = engines.refresh()
        loading.value = false
    }

    LaunchedEffect(engines) { reload() }

    // The graphs are sampled on a timer of their own. The list is only fetched
    // when somebody asks (opening the screen, pulling refresh), but a graph is
    // history: one reading is a number, not a line.
    //
    // The same sample decides the card's light. The registry's `online` is a
    // lease read once, at fetch time: it is up to a minute old on arrival and
    // frozen afterwards, so a machine that died a second later was drawn as up
    // for as long as the screen stayed open. Two sources of truth where one is
    // stale is exactly a light that lies, so a card's word is only ever the
    // phone's own last answer, and the registry's lease is the word until the
    // first answer arrives (right after the fetch, when the lease is still
    // fresh).
    //
    // A card is not the only thing that changes under this blur: every sample
    // re-renders the layer the blur is applied to — which on this phone means
    // the whole page visibly re-blurs every two seconds. So the timer stands
    // down while the menu is up. What is behind the blur is being decided about,
    // not watched, and it is sampled again the moment the menu closes.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(engines, snapshot.value.engines, menuOpen.targetState) {
        val listed = snapshot.value.engines
        if (listed.isEmpty()) return@LaunchedEffect
        if (menuOpen.targetState) return@LaunchedEffect
        val plan = ProbePlan()
        var tick = 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                // Asked together: one dead engine must not hold the others'
                // numbers back for the length of its own timeout.
                val due = listed.filter { plan.due(it.id, live[it.id], tick) }
                coroutineScope {
                    due.map { engine ->
                        async {
                            val health = engines.health(engine.url)
                            live[engine.id] = health != null
                            if (health != null) readings.record(engine.id, health)
                        }
                    }.awaitAll()
                }
                plan.forget(listed.map { it.id })
                tick++
                delay(HEALTH_MS)
            }
        }
    }

    // While the tiles are up the list goes out of focus: the blur says "the page
    // is not what you are touching", and the veil under them takes the taps that
    // miss a tile.
    //
    // The blur is switched, not eased: a full-screen blur whose radius moves has
    // to be recomputed every frame, and on a phone with no GPU that recompute
    // costs more than the motion is worth — measured at ~3 frames a second.
    //
    // It is switched at the *start* of the animation (`targetState`), not at the
    // end (`currentState`): switching it late makes the page recede in a second,
    // separate event after the tiles have already landed, which reads as a
    // flash. Switched at once, the page recedes while the veil is arriving and
    // the only thing that moves afterwards is the veil and the tiles.
    Box(Modifier.fillMaxSize()) {
    Box(
        Modifier.fillMaxSize().then(
            if (menuOpen.targetState) Modifier.blur(BLUR_DP.dp) else Modifier,
        ),
    ) {
    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.width(268.dp),
            ) {
                Column(Modifier.fillMaxSize().padding(Space.s3)) {
                    DrawerNavRow(
                        icon = Icons.Default.Devices,
                        label = "Engines",
                        current = true,
                        onClick = { scope.launch { drawer.close() } },
                    )
                    DrawerNavRow(
                        icon = Icons.Default.Smartphone,
                        label = "Droidime",
                        current = false,
                        onClick = { scope.launch { drawer.close() }; onOpenDroidime() },
                    )
                    // The same controls the chat's drawer carries, at the same
                    // depth: a key that ran out is repaired from either screen.
                    DrawerNavRow(
                        icon = Icons.Default.Key,
                        label = "Providers",
                        current = false,
                        onClick = { scope.launch { drawer.close() }; onOpenProviders() },
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(MaterialTheme.aixodia.line),
                    )
                    DrawerNavRow(
                        icon = Icons.Default.Settings,
                        label = "Settings",
                        current = false,
                        onClick = { scope.launch { drawer.close() }; onOpenSettings() },
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // The console's bar: the product, a menu, one control. The list's
                // own title sits under it, where the console puts a heading.
                ConsoleBar(
                    onMenu = { scope.launch { drawer.open() } },
                    menuLabel = "Engine menu",
                    onAction = { scope.launch { reload() } },
                    actionLabel = "Refresh engine list",
                    actionIcon = Icons.Default.Refresh,
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Space.s2,
                    // The system's own bar is not ours to draw under: the last card's
                    // More button was sitting inside the gesture area, where a tap is
                    // the system's, not the card's. The list ends where the bar begins.
                    bottom = Space.s4 + WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                item(key = "screen-head") {
                    ScreenHead(
                        title = "Engines",
                        meta = "${snapshot.value.engines.size} engines",
                    )
                }
                item(key = "updated") {
                    Text(
                        if (snapshot.value.fetchedAt > 0) "Updated just now"
                        else "Showing the last saved list",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (loading.value && snapshot.value.engines.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(Space.s5), Alignment.Center) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                            )
                        }
                    }
                }
                if (!loading.value && snapshot.value.engines.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(Space.s5),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "No engines in the registry. Start an engine, then refresh.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(Space.s3))
                            Button(onClick = { scope.launch { reload() } }) { Text("Refresh") }
                        }
                    }
                }
                items(snapshot.value.engines, key = { it.id }) { engine ->
                    EngineCard(
                        engine = engine,
                        online = live[engine.id] ?: engine.online,
                        busy = message.value == engine.id,
                        readings = windows[engine.id],
                        onOpen = { pane ->
                            message.value = engine.id
                            scope.launch {
                                val probe = engines.probe(engine.url)
                                message.value = ""
                                if (probe.ok) {
                                    engines.select(engine.id)
                                    onOpen(engine, probe, pane)
                                } else {
                                    message.value = "Cannot reach ${engine.displayName}: ${probe.error.ifBlank { "not an engine" }}"
                                }
                            }
                        },
                        onMore = { openMenu(engine) },
                    )
                }
                if (message.value.startsWith("Cannot reach")) {
                    item(key = "error") {
                        Text(
                            message.value,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
    }

    // The verbs a card has no room for, as buttons over the blurred list.
    // Each one selects the engine whose More was pressed first: the phone
    // addresses one engine at a time through the address it stores, and a button
    // that asked engine B while the address said A would be asking the wrong
    // machine.
    menuEngine.value?.let { engine ->
        EngineActions(
            engine = engine,
            open = menuOpen,
            working = menuWorking.value,
            note = menuNote.value,
            onUpdate = {
                menuWorking.value = UPDATE
                menuNote.value = ""
                scope.launch {
                    engines.select(engine.id)
                    menuNote.value = history.requestUpdate().headline()
                    menuWorking.value = ""
                }
            },
            onBackup = {
                menuWorking.value = BACKUP
                menuNote.value = ""
                scope.launch {
                    engines.select(engine.id)
                    menuNote.value = history.backupStart()
                    menuWorking.value = ""
                }
            },
            onFiles = {
                closeMenu()
                message.value = engine.id
                scope.launch {
                    val probe = engines.probe(engine.url)
                    message.value = ""
                    if (probe.ok) {
                        engines.select(engine.id)
                        onOpen(engine, probe, Place.Files)
                    } else {
                        message.value = "Cannot reach ${engine.displayName}: " +
                            probe.error.ifBlank { "not an engine" }
                    }
                }
            },
            // A name and a deletion are the gateway's two writes, and both are
            // asked in a dialog rather than done on the tile: one replaces what
            // the reader typed, the other cannot be undone.
            onRename = { renameEngine.value = engine; renameText.value = engine.alias },
            onDelete = { deleteEngine.value = engine },
            onDismiss = { closeMenu() },
        )
    }

    // A name is only a caption, so the dialog says so: the id the machine answers
    // on is not changed by anything typed here.
    renameEngine.value?.let { engine ->
        AlertDialog(
            onDismissRequest = { renameEngine.value = null },
            title = { Text("Name this engine") },
            text = {
                Column {
                    Text(
                        "Shown in the app instead of ${engine.id}. The machine's id does not change.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(Space.s3))
                    OutlinedTextField(
                        value = renameText.value,
                        onValueChange = { renameText.value = it.take(ALIAS_MAX) },
                        singleLine = true,
                        label = { Text("Name") },
                        placeholder = { Text(engine.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = menuWorking.value.isEmpty(),
                    onClick = {
                        val wanted = renameText.value
                        renameEngine.value = null
                        menuWorking.value = RENAME
                        menuNote.value = ""
                        scope.launch {
                            menuNote.value = engines.rename(engine.id, wanted).headline()
                            menuWorking.value = ""
                            reload()
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameEngine.value = null }) { Text("Cancel") }
            },
        )
    }

    // Deleting is the one irreversible verb in this screen, so it is asked about
    // in words, with the machine's name in them, and with what it does: the row,
    // the hostname and the tunnel.
    deleteEngine.value?.let { engine ->
        AlertDialog(
            onDismissRequest = { deleteEngine.value = null },
            title = { Text("Delete ${engine.displayName}?") },
            text = {
                Text(
                    "It is removed from the engine list for every client, and its address is " +
                        "taken down with it: the DNS record, the tunnel and the keys that made it " +
                        "reachable. The machine itself is not touched, and an engine that starts " +
                        "again makes a fresh address for itself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = menuWorking.value.isEmpty(),
                    onClick = {
                        deleteEngine.value = null
                        menuWorking.value = DELETE
                        menuNote.value = ""
                        // Whose address this phone is dialling, before the list
                        // changes under it.
                        val wasActive = snapshot.value.active?.id == engine.id
                        scope.launch {
                            val answer = engines.delete(engine.id)
                            menuNote.value = answer.headline()
                            menuWorking.value = ""
                            reload()
                            if (answer is EngineDeleteAnswer.Deleted && wasActive) {
                                // A phone pointed at the row that just went away
                                // has to be pointed somewhere that exists.
                                snapshot.value.engines
                                    .firstOrNull { it.id != engine.id }
                                    ?.let { engines.select(it.id) }
                            }
                        }
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteEngine.value = null }) { Text("Cancel") }
            },
        )
    }
    }
}

/** A row of the console's drawer: an icon and a name, the current one marked. */
@Composable
internal fun DrawerNavRow(
    icon: ImageVector,
    label: String,
    current: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .then(
                if (current) Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.aixodia.line, MaterialTheme.shapes.small)
                else Modifier,
            )
            .padding(horizontal = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (current) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Space.s2))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (current) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One engine, drawn as the console draws a machine: a head with a state light,
 * a band of three readings over their last few minutes, and a row of four equal
 * actions — each band closed by a hairline. The readings come from the engine's
 * own `/healthz`, sampled by the screen while the card is on screen.
 */
@Composable
internal fun EngineCard(
    engine: EngineEntry,
    online: Boolean,
    busy: Boolean,
    onOpen: (Place) -> Unit,
    onMore: () -> Unit,
    readings: EngineReadings? = null,
) {
    val offline = !online
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (offline) MaterialTheme.colorScheme.surfaceContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.medium,
            )
            .border(1.dp, MaterialTheme.aixodia.line, MaterialTheme.shapes.medium)
            .padding(Space.s4),
    ) {
        // Band one — who it is, and whether it is up. State is a light and a
        // duration, not a pill.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                engine.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.size(Space.s3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(6.dp).background(
                        if (offline) MaterialTheme.aixodia.offline
                        else MaterialTheme.aixodia.online,
                        CircleShape,
                    ),
                )
                Spacer(Modifier.size(Space.s2))
                Text(
                    // The word is the state, always: a subtitle that says "up 12h"
                    // beside a red dot is two answers in one row, and the one a
                    // reader believes is the words.
                    if (busy) "checking…"
                    else if (offline) "offline"
                    else if (engine.subtitle.isNotBlank()) "online · ${engine.subtitle}"
                    else "online",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = Space.s3, bottom = Space.s3)
                .height(1.dp).background(MaterialTheme.aixodia.line),
        )

        // Band two — three readings, held apart by hairlines the way the
        // console separates its columns. Each one is the value the engine last
        // reported over the shape of its window; an engine that has not answered
        // yet keeps the console's em dash rather than a zero. Memory and disk are
        // said as used over whole, which is how the web says them.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            MetricCell(
                label = "CPU",
                icon = Icons.Filled.Memory,
                series = readings?.cpu.orEmpty(),
                digits = 0,
                // A share of what: the cores this engine may use, when the engine
                // says so. `12%` alone reads the same on a phone-sized engine and
                // on a thirty-two-core host, and those are different machines.
                unit = readings?.cpuCores?.let { "% of $it" } ?: "%",
                total = null,
                ceiling = 100.0,
                first = true,
            )
            MetricCell(
                label = "RAM",
                icon = Icons.Filled.Storage,
                series = readings?.ram.orEmpty(),
                digits = MetricShape.decimalsFor(readings?.ramTotalGb),
                unit = "GB",
                total = readings?.ramTotalGb,
                ceiling = readings?.ramTotalGb?.toDoubleOrNull() ?: 100.0,
                first = false,
            )
            MetricCell(
                label = "DISK",
                icon = Icons.Filled.SdStorage,
                series = readings?.disk.orEmpty(),
                digits = MetricShape.decimalsFor(readings?.diskTotalGb),
                unit = "GB",
                total = readings?.diskTotalGb,
                ceiling = readings?.diskTotalGb?.toDoubleOrNull() ?: 100.0,
                first = false,
            )
        }
// An engine's readings are cheap — the endpoint reads the host's own counters —
        // so the phone samples them faster than the console's idle browser does. A
        // card on a phone is looked at for a few seconds, and a number that is
        // twenty seconds old reads as stale. Nothing else is polled at this rate:
        // the list is still fetched only when somebody asks.
        Spacer(Modifier.height(Space.s2))

        // Band three — what you can do. Four peers, none filled: the same
        // icon-above-label action the console draws. Three open a pane; More
        // opens the sheet of everything a card has no room for.
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.aixodia.line))
        Row(
            Modifier.fillMaxWidth().padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            EngineAction(Icons.Default.Terminal, "Terminal", offline || busy) { onOpen(Place.Terminal) }
            EngineAction(Icons.Default.AutoAwesome, "Chat", offline || busy) { onOpen(Place.Chat) }
            EngineAction(Icons.Default.Monitor, "Screen", offline || busy) { onOpen(Place.Screen) }
            // More stays reachable when the machine is not: a dead engine is the
            // one a person most needs to rename or to delete, and both of those
            // are the gateway's writes, not the machine's. The three panes above
            // do need it, so they keep the ordinary rule.
            EngineAction(Icons.Default.MoreHoriz, "More", busy, onClick = onMore)
        }
    }
}

/**
 * One reading in the metrics strip, drawn the way the console draws it: an icon and
 * a name on one line, the number under them with its unit set smaller, and the
 * shape of the window as a texture beneath both. The number is the measurement; the
 * chart only says how the machine has been behaving.
 */
@Composable
internal fun RowScope.MetricCell(
    label: String,
    icon: ImageVector,
    series: List<Double>,
    digits: Int,
    unit: String,
    total: String?,
    ceiling: Double,
    first: Boolean,
) {
    val (value, suffix) = MetricShape.reading(series, digits, unit, total)
    // A ratio too wide for the column takes its unit up with the name, so
    // nothing on the card is clipped into a different unit. A single share
    // (`16 % of 4`) is short enough by construction and keeps its unit.
    val unitMoved = total != null && !MetricShape.unitFits(value, suffix)
    val name = if (unitMoved) "$label $suffix" else label
    val after = if (unitMoved) "" else suffix
    Row(Modifier.weight(1f)) {
        if (!first) {
            Box(
                Modifier
                    .padding(end = Space.s3)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.aixodia.line),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon, contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(Space.s1))
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(Space.s1))
            Row {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                if (after.isNotBlank()) {
                    Text(
                        after,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        modifier = Modifier.padding(start = Space.s1),
                    )
                }
            }
            Spacer(Modifier.height(Space.s1))
            Sparkline(series, ceiling, Modifier.fillMaxWidth().height(18.dp))
        }
        Spacer(Modifier.size(Space.s3))
    }
}

/**
 * The shape of a series, oldest at the left, in the console's geometry: scaled to
 * its own window with a floor on the span, so a machine holding steady is a flat
 * line and not a cliff. A single sample is a level line, and nothing sampled is no
 * line at all.
 */
@Composable
private fun Sparkline(series: List<Double>, ceiling: Double, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    val geometry = MetricShape.points(series, ceiling)
    Canvas(modifier) {
        if (geometry.size < 2) return@Canvas
        fun x(point: Pair<Double, Double>): Float = (point.first / 100.0 * size.width).toFloat()
        fun y(point: Pair<Double, Double>): Float = (point.second / 100.0 * size.height).toFloat()

        val line = Path()
        geometry.forEachIndexed { index, point ->
            if (index == 0) line.moveTo(x(point), y(point)) else line.lineTo(x(point), y(point))
        }
        // The area stops at the chart's own floor, not at the cell's edge, so the
        // line keeps the margins the geometry gave it.
        val floor = (MetricShape.FLOOR / 100.0 * size.height).toFloat()
        val area = Path().apply {
            addPath(line)
            lineTo(x(geometry.last()), floor)
            lineTo(x(geometry.first()), floor)
            close()
        }
        drawPath(area, ink.copy(alpha = 0.14f))
        drawPath(
            line,
            ink.copy(alpha = 0.62f),
            style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** A console action: a square-ish cell, an icon above a one-word label. */
@Composable
internal fun RowScope.EngineAction(
    icon: ImageVector,
    label: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f)
            .height(52.dp)
            .clickable(enabled = !disabled, onClick = onClick)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.shapes.medium,
            )
            .border(1.dp, MaterialTheme.aixodia.line, MaterialTheme.shapes.medium)
            .padding(vertical = Space.s1),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant
                   else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
