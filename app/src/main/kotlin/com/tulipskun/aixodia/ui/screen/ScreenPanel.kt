package com.tulipskun.aixodia.ui.screen

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulipskun.aixodia.data.remote.ScreenSocket
import kotlin.math.roundToInt

/**
 * The engine's screen, on the phone, under a finger.
 *
 * This is the VNC-shaped part without the VNC: the engine streams tiles of the
 * display and this view paints them, then turns touches into pointer events the
 * same way a remote-desktop viewer does — tap is a click, a held drag is a drag,
 * two fingers scroll, and a long press is the right button.
 *
 * All the picture state lives in [ScreenSurface] rather than in Compose: a frame
 * arrives tens of times a second, and rebuilding a composable that often would
 * spend more time in the framework than in drawing. Compose is only told about
 * the things a person reads — the connection state and the error.
 */
@Composable
fun ScreenPanel(
    socket: ScreenSocket,
    baseUrl: String,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by socket.state.collectAsStateWithLifecycle()
    var hello by remember { mutableStateOf<ScreenSocket.Hello?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableIntStateOf(62) }
    var mode by remember { mutableStateOf(InputMode.TRACKPAD) }
    var codec by remember { mutableStateOf("jpeg") }
    var keyboardShown by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    val surface = remember { ScreenSurface() }
    var view by remember { mutableStateOf<ScreenView?>(null) }
    // The video decoder, created only when the engine says it is sending video.
    // Building one for a JPEG stream would take a decoder from another app for
    // nothing.
    val video = remember { VideoSurface(onFirstFrame = {}, onNeedKeyframe = { socket.requestKeyframe() }) }
    var videoMode by remember { mutableStateOf(false) }
    var videoHasPicture by remember { mutableStateOf(false) }

    // The reader's choices for this pane: the way a finger becomes a pointer, a
    // chosen mouse button, and how the picture travels. They are remembered on
    // the phone so a revisit opens the way the last visit ended, including after
    // an engine restart that reset the engine's own settings.
    LaunchedEffect(Unit) {
        mode = InputMode.parse(socket.savedInputMode())
    }

    // The view owns the fingers; the panel owns the choices. Handed over rather
    // than read live, so a choice stays put through a redial.
    LaunchedEffect(mode) {
        view?.inputMode = mode
    }

    DisposableEffect(Unit) {
        onDispose { video.stop() }
    }

    // The picture is only worth its bandwidth while someone is looking at it, so
    // the link follows the app rather than the pane: leaving the app closes it
    // and the engine stops encoding, and coming back dials again for a whole
    // frame. That also fixes what a kept-warm socket cannot: a process frozen
    // for a minute still believes its socket is open, because nothing in it ran
    // to notice the tunnel was gone.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, baseUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (socket.state.value == ScreenSocket.State.CLOSED) socket.reconnect()
                }
                Lifecycle.Event.ON_STOP -> socket.close()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        socket.connect(baseUrl)
        onDispose {
            lifecycle.removeObserver(observer)
            socket.close()
        }
    }

    LaunchedEffect(socket) {
        socket.stream.collect { event ->
            when (event) {
                is ScreenSocket.Event.Ready -> {
                    hello = event.hello
                    // The engine reports the quality it is encoding at, which is
                    // its own default until this phone says otherwise. A reader
                    // who chose a sharpness expects it back on the next visit, so
                    // it is asked for again when it differs.
                    val saved = socket.savedQuality()
                    quality = event.hello.quality
                    if (saved != null && saved != event.hello.quality) {
                        quality = saved
                        socket.settings(quality = saved)
                    }
                    // Same for the codec: the engine answers with the one it is
                    // encoding, and a phone that chose video wants video back.
                    codec = event.hello.codec
                    val wantedCodec = socket.savedCodec()
                    if (wantedCodec != null && wantedCodec != event.hello.codec) {
                        codec = wantedCodec
                        socket.settings(codec = wantedCodec)
                    }
                    failure = null
                    // A codec change is a new picture: the surface is resized and
                    // the decoder rebuilt, because frames from the old one cannot
                    // be painted into the new.
                    surface.sizeTo(event.hello.width, event.hello.height)
                    // The engine decides; the client does not ask for video it
                    // cannot be given.
                    videoMode = event.hello.codec == "h264"
                    if (videoMode) video.sizeTo(event.hello.width, event.hello.height)
                }
                is ScreenSocket.Event.Video -> {
                    // A video frame goes straight to the decoder; there are no
                    // tiles to paint into a bitmap.
                    videoMode = true
                    codec = "h264"
                    video.submit(event.accessUnit, event.keyframe)
                    surface.cursorTo(event.cursor)
                    view?.invalidateFrame()
                    if (video.hasPicture) videoHasPicture = true
                }
                is ScreenSocket.Event.Patch -> {
                    // Tiles mean the engine went back to JPEG while this socket was
                    // open. The video decoder is given up rather than left holding
                    // a surface for a picture that is no longer video, and the
                    // bitmap path takes over again.
                    if (videoMode) {
                        videoMode = false
                        videoHasPicture = false
                        video.stop()
                    }
                    surface.cursorTo(event.cursor)
                    surface.paint(event)
                    // The frame is one bitmap, so a repaint is a single draw; no
                    // composable re-runs for it.
                    view?.invalidateFrame()
                }
                is ScreenSocket.Event.Failed -> {
                    videoHasPicture = false
                    failure = event.reason
                    // The picture on screen is now a photograph of the past: say
                    // so rather than leaving it looking live.
                    surface.clear()
                    view?.cleared()
                }
                ScreenSocket.Event.Closed -> {
                    // The redial loop is already running. Dropping the greeting
                    // puts the spinner back, which is honest: there is no picture
                    // behind this black rectangle until the next hello.
                    hello = null
                    surface.clear()
                    view?.cleared()
                }
            }
        }
    }

    // The system bars are drawn over the app (edge-to-edge), so the picture is
    // laid out inside them: without this the quality strip sits under the
    // navigation bar and cannot be reached.
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The decoded video sits under the touch view, which is transparent
            // and owns the gestures: one view reads the fingers whether the
            // picture beneath it is a bitmap or a video surface.
            if (videoMode) {
                AndroidView(
                    factory = { context ->
                        TextureView(context).also { textured ->
                            textured.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    texture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) {
                                    video.attach(Surface(texture))
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    texture: SurfaceTexture,
                                    width: Int,
                                    height: Int,
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture) = true

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            AndroidView(
                factory = { context ->
                    ScreenView(context).also {
                        it.attach(socket, surface)
                        // In video mode the arrow is drawn over the video rather
                        // than into a bitmap, and the picture itself is not drawn
                        // here at all.
                        it.videoMode = videoMode
                        view = it
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            val problem = failure
            when {
                problem != null -> Centered(problem)
                // Before the first frame there is nothing to look at. A spinner
                // says the picture is coming; a black rectangle says the feature
                // is broken, and both are the same black rectangle otherwise.
                hello == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
        ScreenBar(
            hello = hello,
            state = state,
            quality = quality,
            mode = mode,
            codec = codec,
            keyboard = keyboardShown,
            expanded = showControls,
            socket = socket,
            onLeave = onLeave,
            onToggle = { showControls = !showControls },
            onKeyboard = { keyboardShown = !keyboardShown },
            onQuality = { value ->
                quality = value
                socket.settings(quality = value)
            },
            onQualitySettled = { socket.keepQuality(quality) },
            onMode = { wanted ->
                mode = wanted
                socket.keepInputMode(wanted.stored)
            },
            onCodec = { wanted ->
                codec = wanted
                socket.settings(codec = wanted)
                socket.keepCodec(wanted)
            },
            onHold = { button -> view?.holdButton(button) },
            onRelease = { view?.releaseButton() },
            onFit = { if (view?.isActualSize() == true) view?.fitScreen() else view?.actualSize() },
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/**
 * The strip under the picture: where back goes, what the link is doing, and the
 * controls a phone cannot do without — how a finger becomes a pointer, which
 * mouse button is down, how sharp the picture is, and which codec carries it.
 */
@Composable
private fun ScreenBar(
    hello: ScreenSocket.Hello?,
    state: ScreenSocket.State,
    quality: Int,
    mode: InputMode,
    codec: String,
    keyboard: Boolean,
    expanded: Boolean,
    socket: ScreenSocket,
    onLeave: () -> Unit,
    onToggle: () -> Unit,
    onKeyboard: () -> Unit,
    onQuality: (Int) -> Unit,
    onQualitySettled: () -> Unit,
    onMode: (InputMode) -> Unit,
    onCodec: (String) -> Unit,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    onFit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The screen is opened from an engine card, so the way back is the
                // engine list — the same place the terminal's back goes, and not
                // the chat, which is a different thing about the same machine.
                Text(
                    "←",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(end = 14.dp)
                        .pointerTap(onLeave, size = 20.dp),
                )
                Text(
                    when {
                        // A still screen sends nothing, so "open" is the only
                        // honest word for a link that is up but quiet.
                        state == ScreenSocket.State.OPEN && hello != null ->
                            "${hello.width}×${hello.height} · ${hello.fps}fps"
                        state == ScreenSocket.State.CLOSED -> "not connected"
                        else -> "connecting…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The keys a phone keyboard does not have: Escape, Tab, arrows,
                // function keys, and a field that types any language.
                Text(
                    "⌨",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .pointerTap(onKeyboard, size = 20.dp),
                )
                Text(
                    "${quality}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        // The controls no phone can assume: the input mode, the button being
        // held, the fit, and the codec. A row narrow enough to need no drawer;
        // scrolling moves left and right within it.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Chip(selected = mode == InputMode.TRACKPAD, label = "trackpad", onTap = { onMode(InputMode.TRACKPAD) })
            Chip(selected = mode == InputMode.DIRECT, label = "touch", onTap = { onMode(InputMode.DIRECT) })
            Gap()
            val buttons = PointerPlan.BUTTONS
            for (name in buttons) {
                HoldChip(label = PointerPlan.label(name), onHold = { onHold(name) }, onRelease = onRelease)
            }
            Gap()
            Chip(selected = false, label = "fit · 1:1", onTap = { onFit() })
            Gap()
            Chip(selected = codec == "h264", label = "h264", onTap = { onCodec("h264") })
            Chip(selected = codec != "h264", label = "jpeg", onTap = { onCodec("jpeg") })
        }
        if (keyboard) {
            KeyboardBar(socket)
        }
        if (expanded) {
            Slider(
                value = quality.toFloat(),
                onValueChange = { onQuality(it.roundToInt()) },
                onValueChangeFinished = onQualitySettled,
                valueRange = 10f..95f,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                "tap to adjust quality",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp).pointerTap(onToggle, size = 14.dp),
            )
        }
    }
}

/** One labelled option, highlighted while it is the one chosen. */
/** A thin rule between groups of controls. */
@Composable
private fun Gap() {
    Box(
        Modifier.width(1.dp).height(16.dp).background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun Chip(selected: Boolean, label: String, onTap: () -> Unit) {
    val background =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .pointerTap(onTap, size = 14.dp),
    )
}

/**
 * A mouse button that stays down for as long as it is pressed.
 *
 * This is the viewer's mouse panel: hold it, drag with one finger, let go — a
 * window is carried, not clicked.
 */
@Composable
private fun HoldChip(label: String, onHold: () -> Unit, onRelease: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .pointerInput(label) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Press) continue
                        onHold()
                        // Hold the button for the whole press, even if the finger
                        // wanders; release when the finger lifts — and on the way
                        // out too, because a button left down on the machine is a
                        // window being dragged by nobody.
                        try {
                            while (true) {
                                val next = awaitPointerEvent()
                                if (next.type == PointerEventType.Release) break
                                if (next.changes.all { !it.pressed }) break
                            }
                        } finally {
                            onRelease()
                        }
                    }
                }
            },
    )
}

@Composable
private fun Modifier.pointerTap(onTap: () -> Unit, size: androidx.compose.ui.unit.Dp): Modifier =
    this.pointerInput(size) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press) onTap()
            }
        }
    }.padding(vertical = 2.dp)
