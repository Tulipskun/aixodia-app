package com.tulipskun.aixodia.ui.droidime

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tulipskun.aixodia.DeviceEntry
import com.tulipskun.aixodia.data.remote.DeviceDirectory
import com.tulipskun.aixodia.ui.display.ConsoleBar
import com.tulipskun.aixodia.ui.display.ScreenHead
import com.tulipskun.aixodia.ui.engines.DrawerNavRow
import com.tulipskun.aixodia.ui.engines.EngineAction
import com.tulipskun.aixodia.ui.engines.MetricCell
import com.tulipskun.aixodia.ui.theme.Space
import com.tulipskun.aixodia.ui.theme.aixodia
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the phone asks each device whether it is answering. */
private const val DEVICE_MS = 5_000L

/**
 * The phones DroidIME runs on, drawn as their own section.
 *
 * A separate screen, not a second list under the engines: a device is watched or
 * driven, and offers neither a shell nor a desktop. On the web the two are two
 * entries in the menu and two listings in one vocabulary; here the same split is
 * a place of its own in the app's history, so back from it lands on the engines
 * and the drawer says which of the two is showing.
 *
 * The two verbs are drawn and not pressable, exactly as the console draws them:
 * the device side of "view" and "control" does not exist yet, and a button that
 * pretends otherwise is worse than a button that says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DroidimeScreen(
    devices: DeviceDirectory,
    onOpenEngines: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProviders: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val listed = remember { mutableStateOf<List<DeviceEntry>>(emptyList()) }
    val failure = remember { mutableStateOf("") }
    // The registry's lease is a hint; the phone's own answer is the word, for the
    // same reason the engine cards stopped trusting a lease read once.
    val live = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun reload() {
        when (val read = devices.devices()) {
            is DeviceDirectory.DeviceRead.Listing -> {
                listed.value = read.devices
                failure.value = ""
            }
            is DeviceDirectory.DeviceRead.Failed -> failure.value = read.reason
        }
    }

    LaunchedEffect(devices) { reload() }

    BackHandler(enabled = drawer.isOpen || drawer.targetValue != DrawerValue.Closed) {
        scope.launch { drawer.close() }
    }

    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(listed.value) {
        val known = listed.value
        if (known.isEmpty()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                coroutineScope {
                    known.map { device ->
                        async { live[device.id] = devices.health(device.id) }
                    }.awaitAll()
                }
                delay(DEVICE_MS)
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
                        current = false,
                        onClick = { scope.launch { drawer.close() }; onOpenEngines() },
                    )
                    DrawerNavRow(
                        icon = Icons.Default.Smartphone,
                        label = "Droidime",
                        current = true,
                        onClick = { scope.launch { drawer.close() } },
                    )
                    Spacer(Modifier.weight(1f))
                    DrawerNavRow(
                        icon = Icons.Default.Key,
                        label = "Providers",
                        current = false,
                        onClick = { scope.launch { drawer.close() }; onOpenProviders() },
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
                ConsoleBar(
                    onMenu = { scope.launch { drawer.open() } },
                    menuLabel = "Device menu",
                    onAction = { scope.launch { reload() } },
                    actionLabel = "Refresh device list",
                    actionIcon = Icons.Default.Refresh,
                )
            },
        ) { pad ->
            LazyColumn(
                Modifier.fillMaxSize().padding(pad).padding(horizontal = Space.s3),
                verticalArrangement = Arrangement.spacedBy(Space.s3),
                contentPadding = PaddingValues(
                    top = Space.s2,
                    // The list ends where the system's bar begins, so the last
                    // device's row is not under the buttons that dismiss it.
                    bottom = Space.s4 + WindowInsets.navigationBars
                        .asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                item(key = "head") {
                    ScreenHead(
                        title = "Droidime",
                        meta = when {
                            failure.value.isNotBlank() -> "not read"
                            else -> "${listed.value.size} devices"
                        },
                    )
                }
                if (failure.value.isNotBlank()) {
                    item(key = "failure") {
                        Text(
                            "The device list could not be read: ${failure.value}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Space.s5),
                        )
                    }
                }
                items(listed.value, key = { it.id }) { device ->
                    DeviceCard(device = device, online = live[device.id] ?: device.online)
                }
            }
        }
    }
}

/**
 * One device, drawn as the console draws a phone: who it is and whether it is
 * answering, the three readings a phone has, and the two verbs it offers.
 *
 * There are no readings yet — nothing on a device reports CPU, memory or battery
 * to this registry — so all three keep the console's em dash. A dash is the
 * honest answer to "not measured"; a zero would be a claim about a machine
 * nobody asked.
 */
@Composable
internal fun DeviceCard(device: DeviceEntry, online: Boolean) {
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
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                device.displayName,
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
                    if (offline) "offline"
                    else if (device.subtitle.isNotBlank()) "online · ${device.subtitle}"
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

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            MetricCell(
                label = "CPU",
                icon = Icons.Filled.Memory,
                series = emptyList(),
                digits = 0,
                unit = "%",
                total = null,
                ceiling = 100.0,
                first = true,
            )
            MetricCell(
                label = "RAM",
                icon = Icons.Filled.Storage,
                series = emptyList(),
                digits = 1,
                unit = "GB",
                total = null,
                ceiling = 100.0,
                first = false,
            )
            MetricCell(
                label = "BATTERY",
                icon = Icons.Filled.BatteryFull,
                series = device.battery?.let { listOf(it.toDouble()) }.orEmpty(),
                digits = 0,
                unit = "%",
                total = null,
                ceiling = 100.0,
                first = false,
            )
        }

        Spacer(Modifier.height(Space.s2))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.aixodia.line))
        Row(
            Modifier.fillMaxWidth().padding(top = Space.s3),
            horizontalArrangement = Arrangement.spacedBy(Space.s2),
        ) {
            EngineAction(Icons.Default.Visibility, "View screen", disabled = true) {}
            EngineAction(Icons.Default.Mouse, "Control screen", disabled = true) {}
        }
        Spacer(Modifier.height(Space.s2))
        Text(
            "Viewing a device's screen is not wired yet: the registry can say whether the " +
                "phone is answering, and nothing more.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
