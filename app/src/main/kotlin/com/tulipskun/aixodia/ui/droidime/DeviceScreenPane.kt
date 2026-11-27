package com.tulipskun.aixodia.ui.droidime

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.DeviceEntry
import com.tulipskun.aixodia.data.remote.DeviceDirectory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How often the picture is taken again while the pane is open. A phone's screen
 * is a picture, not a stream: DroidIME's MCP server answers `screenshot` with a
 * PNG, so watching means asking again. One second is the same rhythm the engine
 * cards are sampled at, and it keeps a phone on a mobile link from spending its
 * battery on a screen nobody is touching.
 */
private const val FRAME_MS = 1_000L

/**
 * A DroidIME device's screen, drawn from the pictures its own server returns.
 *
 * Two verbs, one picture: "View screen" opens it read-only and "Control screen"
 * opens it tappable. A tap is spoken in the device's own pixels — the picture is
 * smaller than the screen it came from, and it is fitted into this pane's shape
 * with bars — so the mapping lives in [DevicePoint] where a test can pin it. A
 * tap on the bars is not sent anywhere, because that is not a place on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreenPane(
    devices: DeviceDirectory,
    device: DeviceEntry,
    control: Boolean,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var picture by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var deviceSize by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var failure by remember { mutableStateOf("") }
    var watching by remember { mutableStateOf(true) }

    // The size a tap is spoken in, asked once: a phone does not change its screen
    // size while it is being watched, and asking every frame would double the
    // round trips for a number that never moves.
    LaunchedEffect(device.id) {
        deviceSize = devices.screenSize(device.id)
    }

    LaunchedEffect(device.id, watching) {
        while (watching) {
            val bytes = devices.screen(device.id)
            if (bytes == null) {
                failure = "The phone did not answer. Its MCP server may be down, or the tunnel with it."
            } else {
                val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (decoded == null) {
                    failure = "The phone sent a picture this app could not read."
                } else {
                    picture = decoded
                    failure = ""
                }
            }
            delay(FRAME_MS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device.displayName) },
                navigationIcon = {
                    IconButton(onClick = {
                        watching = false
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .onSizeChanged { box = it },
            ) {
                val drawn = picture
                if (drawn == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                } else {
                    Image(
                        bitmap = drawn.asImageBitmap(),
                        contentDescription = "The screen of ${device.displayName}",
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!control) Modifier else Modifier.pointerInput(drawn, box, deviceSize) {
                                    detectTapGestures { at ->
                                        val size = deviceSize ?: return@detectTapGestures
                                        val spot = DevicePoint.tap(
                                            x = at.x,
                                            y = at.y,
                                            boxWidth = box.width.toFloat(),
                                            boxHeight = box.height.toFloat(),
                                            pictureWidth = drawn.width,
                                            pictureHeight = drawn.height,
                                            deviceWidth = size.first,
                                            deviceHeight = size.second,
                                        ) ?: return@detectTapGestures
                                        scope.launch { devices.tap(device.id, spot.x, spot.y) }
                                    }
                                }
                            ),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        failure.isNotBlank() -> failure
                        control -> "Tap the picture to touch the phone."
                        else -> "Watching. Open \"Control screen\" to touch it."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
