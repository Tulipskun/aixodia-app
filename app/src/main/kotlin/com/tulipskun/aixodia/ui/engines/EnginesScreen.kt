package com.tulipskun.aixodia.ui.engines

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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.data.remote.EngineDirectory
import com.tulipskun.aixodia.data.remote.Health
import com.tulipskun.aixodia.data.remote.HistoryApi
import com.tulipskun.aixodia.readings.EngineReadings
import com.tulipskun.aixodia.readings.EngineReadingsStore
import com.tulipskun.aixodia.readings.MetricShape
import com.tulipskun.aixodia.ui.display.ConsoleBar
import com.tulipskun.aixodia.ui.display.ScreenHead
import com.tulipskun.aixodia.ui.theme.aixodia
import com.tulipskun.aixodia.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the cards re-read an engine's `/healthz`. */
private const val HEALTH_MS = 2_000L

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
    onOpen: (EngineEntry, EngineDirectory.Probe, String) -> Unit,
    onOpenSettings: () -> Unit,
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
    val menuWorking = remember { mutableStateOf("") }
    val menuNote = remember { mutableStateOf("") }
    // What each card's strip draws. The windows belong to the app, not to this
    // screen: leaving the list for a thread and coming back must not empty them.
    val windows by readings.byEngine.collectAsState()

    suspend fun reload() {
        loading.value = true
        snapshot.value = engines.refresh()
        loading.value = false
    }

    LaunchedEffect(engines) { reload() }

    // The graphs are sampled on a timer of their own. The list is only fetched
    // when somebody asks (opening the screen, pulling refresh), but a graph is
    // history: one reading is a number, not a line. Only engines the gateway
    // reports up are asked, so a card whose light is off does not spend every
    // sample waiting out a timeout, and the timer only runs while the screen is
    // the one being looked at: a phone in a pocket has no cards to keep current.
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(engines, snapshot.value.engines) {
        val listed = snapshot.value.engines
        if (listed.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                for (engine in listed) {
                    if (!engine.online) continue
                    val health = engines.health(engine.url) ?: continue
                    readings.record(engine.id, health)
                }
                delay(HEALTH_MS)
            }
        }
    }

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
                    onRefresh = { scope.launch { reload() } },
                    refreshLabel = "Refresh engine list",
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Space.s2, bottom = Space.s4,
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
                                    message.value = "Cannot reach ${engine.name}: ${probe.error.ifBlank { "not an engine" }}"
                                }
                            }
                        },
                        onMore = {
                            menuNote.value = ""
                            menuWorking.value = ""
                            menuEngine.value = engine
                        },
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

    // The second shelf of verbs. Every row acts on the engine whose More was
    // pressed, which means selecting it first: the phone addresses one engine at
    // a time through the address it stores, and a menu that asked engine B while
    // the address said A would be asking the wrong machine.
    menuEngine.value?.let { engine ->
        EngineMenu(
            engine = engine,
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
                menuEngine.value = null
                message.value = engine.id
                scope.launch {
                    val probe = engines.probe(engine.url)
                    message.value = ""
                    if (probe.ok) {
                        engines.select(engine.id)
                        onOpen(engine, probe, "files")
                    } else {
                        message.value = "Cannot reach ${engine.name}: " +
                            probe.error.ifBlank { "not an engine" }
                    }
                }
            },
            onDismiss = { menuEngine.value = null },
        )
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
    busy: Boolean,
    onOpen: (String) -> Unit,
    onMore: () -> Unit,
    readings: EngineReadings? = null,
) {
    val offline = !engine.online
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
                engine.name,
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
                    if (busy) "checking…"
                    else engine.subtitle.ifBlank { if (offline) "unreachable" else "online" },
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
            EngineAction(Icons.Default.Terminal, "Terminal", offline || busy) { onOpen("terminal") }
            EngineAction(Icons.Default.AutoAwesome, "AI", offline || busy) { onOpen("chat") }
            EngineAction(Icons.Default.Monitor, "Screen", offline || busy) { onOpen("screen") }
            EngineAction(Icons.Default.MoreHoriz, "More", offline || busy, onClick = onMore)
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
private fun RowScope.MetricCell(
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
