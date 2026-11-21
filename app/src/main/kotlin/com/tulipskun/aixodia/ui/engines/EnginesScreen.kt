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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.data.remote.EngineDirectory
import com.tulipskun.aixodia.data.remote.Health
import com.tulipskun.aixodia.ui.display.ConsoleBar
import com.tulipskun.aixodia.ui.display.ScreenHead
import com.tulipskun.aixodia.ui.theme.aixodia
import com.tulipskun.aixodia.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** How often the cards re-read an engine's `/healthz`. */
private const val HEALTH_MS = 5_000L

/** How many readings a graph keeps: four minutes of a five-second sample. */
private const val SAMPLES = 48

/**
 * One engine's last few readings, newest last. Each series is drawn as its own
 * graph, and a series with one point is a dot the card would rather not draw, so
 * the line appears once there is something to compare it with.
 */
data class EngineReadings(
    val cpu: List<Double> = emptyList(),
    val ram: List<Double> = emptyList(),
    val disk: List<Double> = emptyList(),
) {
    private fun List<Double>.plus(value: Double?): List<Double> =
        if (value == null) this else (this + value).takeLast(SAMPLES)

    fun with(health: Health): EngineReadings =
        EngineReadings(
            cpu = cpu.plus(health.cpuPercent),
            ram = ram.plus(health.memoryPercent),
            disk = disk.plus(health.diskPercent),
        )
}

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
    onOpen: (EngineEntry, EngineDirectory.Probe, String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snapshot = remember { mutableStateOf(EngineDirectory.Snapshot(emptyList(), "", 0)) }
    val loading = remember { mutableStateOf(true) }
    val message = remember { mutableStateOf("") }
    // What each card's strip draws. Keyed by engine id and replaced whole on
    // every sample, so a graph redraws from one readable value.
    val readings = remember { mutableStateMapOf<String, EngineReadings>() }

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
    // five seconds waiting out a timeout.
    LaunchedEffect(engines, snapshot.value.engines) {
        val listed = snapshot.value.engines
        if (listed.isEmpty()) return@LaunchedEffect
        while (true) {
            for (engine in listed) {
                if (!engine.online) continue
                val health = engines.health(engine.url) ?: continue
                readings[engine.id] = (readings[engine.id] ?: EngineReadings()).with(health)
            }
            delay(HEALTH_MS)
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
                        readings = readings[engine.id],
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
        // reported over the shape of the last four minutes; an engine that has
        // not answered yet keeps the console's em dash rather than a zero.
        Row(Modifier.fillMaxWidth()) {
            MetricCell("CPU", readings?.cpu, first = true)
            MetricCell("RAM", readings?.ram, first = false)
            MetricCell("DISK", readings?.disk, first = false)
        }
        // The address is not repeated here: a card is already the engine's
        // name and its state, and the URL is setup, not status.
        val caption = listOfNotNull(
            engine.tag.takeIf { it.isNotBlank() },
            engine.version.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
        if (caption.isNotBlank()) {
            Spacer(Modifier.height(Space.s2))
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Band three — what you can do. Four peers, none filled: the same
        // icon-above-label action the console draws. The first three open a
        // pane; More is drawn like the console's unwired fourth verb and waits
        // for the screen behind it.
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.aixodia.line))
        Row(
            Modifier.fillMaxWidth().padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            EngineAction(Icons.Default.Terminal, "Terminal", offline || busy) { onOpen("terminal") }
            EngineAction(Icons.Default.AutoAwesome, "AI", offline || busy) { onOpen("chat") }
            EngineAction(Icons.Default.Monitor, "Screen", offline || busy) { onOpen("screen") }
            EngineAction(Icons.Default.MoreHoriz, "More", true) { }
        }
    }
}

/**
 * One reading in the metrics strip: a label, the latest value, and the shape of
 * the last few minutes under it, with a hairline down its leading edge. A series
 * that has not been sampled yet is the console's em dash and an empty field — no
 * number is invented to fill the space.
 */
@Composable
private fun RowScope.MetricCell(label: String, series: List<Double>?, first: Boolean) {
    val points = series.orEmpty()
    Row(Modifier.weight(1f)) {
        if (!first) {
            Box(
                Modifier
                    .padding(end = Space.s3)
                    .width(1.dp)
                    .height(52.dp)
                    .background(MaterialTheme.aixodia.line),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Text(
                points.lastOrNull()?.let { it.roundToInt().toString() + "%" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.s1))
            Sparkline(points, Modifier.fillMaxWidth().height(16.dp))
        }
    }
}

/**
 * The shape of a series, newest at the right. It is drawn on a fixed 0–100 scale
 * and a fixed window of [SAMPLES], so a graph does not restretch as it fills and
 * two cards can be compared by eye: the line starts at the right edge and walks
 * left, and the whole strip means the same span of time on every card.
 */
@Composable
private fun Sparkline(series: List<Double>, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        // One reading has no shape: a dot would be mistaken for a flat line, so
        // the cell keeps only the number until there is a second point.
        if (series.size < 2) return@Canvas
        val step = size.width / (SAMPLES - 1).toFloat()
        val baseline = size.height
        fun x(index: Int): Float = size.width - (series.size - 1 - index) * step
        fun y(index: Int): Float =
            size.height - (series[index].coerceIn(0.0, 100.0) / 100.0 * size.height).toFloat()

        val path = Path()
        for (index in series.indices) {
            if (index == 0) path.moveTo(x(0), y(0)) else path.lineTo(x(index), y(index))
        }

        val area = Path().apply {
            addPath(path)
            lineTo(x(series.size - 1), baseline)
            lineTo(x(0), baseline)
            close()
        }
        drawPath(area, ink.copy(alpha = 0.16f))
        drawPath(path, ink, style = Stroke(width = 1.5.dp.toPx()))
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
