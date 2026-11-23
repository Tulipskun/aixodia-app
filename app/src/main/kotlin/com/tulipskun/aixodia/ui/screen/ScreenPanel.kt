package com.tulipskun.aixodia.ui.screen

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tulipskun.aixodia.data.remote.ScreenSocket
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    // A tap asks the machine what it landed in, which takes a moment; a second
    // tap before the answer arrives must not be overruled by the first one.
    var keyboardJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // The viewer's rail: closed until the three dots are pressed, and each pane
    // it opens is remembered separately, the way a viewer does — the mouse pane
    // can stay up while the settings pane is opened and closed again.
    var railOpen by rememberSaveable { mutableStateOf(false) }
    var mouseOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val surface = remember { ScreenSurface() }
    var view by remember { mutableStateOf<ScreenView?>(null) }
    // The video decoder, created only when the engine says it is sending video.
    // Building one for a JPEG stream would take a decoder from another app for
    // nothing.
    val video = remember { VideoSurface(onFirstFrame = {}, onNeedKeyframe = { socket.requestKeyframe() }) }
    var videoMode by remember { mutableStateOf(false) }
    var videoHasPicture by remember { mutableStateOf(false) }
    // Whether this link has parked the arrow in the middle yet. Per link, not
    // per pane: a redial is a new session on a desktop whose pointer the last
    // one left somewhere.
    var centredArrow by remember { mutableStateOf(false) }
    // The size of the picture being shown. A redial keeps the reader's zoom; only
    // a picture that comes back a different shape resets it, because a zoom
    // chosen for one desktop means nothing on a bigger one.
    var pictureSize by remember { mutableStateOf(0 to 0) }
    val centreOnce = {
        if (!centredArrow && view?.centreArrow() == true) centredArrow = true
    }

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
                    if (pictureSize != (event.hello.width to event.hello.height)) {
                        pictureSize = event.hello.width to event.hello.height
                        view?.fitScreen()
                    }
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
                    centreOnce()
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
                    // The first picture of a link parks the arrow in the middle of
                    // the desktop, the way a viewer does on connecting: the
                    // machine's pointer is wherever the last session left it, and
                    // a pad with no arrow in sight has nothing to aim from.
                    centreOnce()
                    // The frame is one bitmap, so a repaint is a single draw; no
                    // composable re-runs for it.
                    view?.invalidateFrame()
                }
                is ScreenSocket.Event.Cursor -> {
                    // The machine's pointer changed shape — a hand, an I-beam, a
                    // watch. The view draws it instead of its own arrow, so the
                    // phone shows what the desktop is showing.
                    view?.setCursorImage(event.width, event.height, event.hotX, event.hotY, event.rgba)
                }
                is ScreenSocket.Event.Failed -> {
                    videoHasPicture = false
                    failure = event.reason
                    // The picture on screen is now a photograph of the past: say
                    // so rather than leaving it looking live.
                    centredArrow = false
                    surface.clear()
                    view?.cleared()
                }
                ScreenSocket.Event.Closed -> {
                    // The redial loop is already running. Dropping the greeting
                    // puts the spinner back, which is honest: there is no picture
                    // behind this black rectangle until the next hello.
                    hello = null
                    centredArrow = false
                    surface.clear()
                    view?.cleared()
                }
            }
        }
    }

    // The system bars are drawn over the app (edge-to-edge), so the picture is
    // laid out inside them: without this the rail sits under the navigation bar
    // and cannot be reached.
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
                        // A tap decides whether the keyboard belongs on screen:
                        // on the picture it is the machine that says so, by
                        // putting an I-beam under the pointer.
                        it.onClick = {
                            keyboardJob?.cancel()
                            keyboardJob = scope.launch {
                                // The click has to reach the machine and the
                                // machine has to move its pointer before its
                                // cursor means anything about what was clicked.
                                delay(KEYBOARD_SETTLE_MS)
                                keyboardShown = socket.cursorRole == ScreenSocket.ROLE_TEXT
                            }
                        }
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
            // The viewer's own chrome: a draggable three-dot rail that opens into
            // a black pill, exactly where RealVNC puts it, so the picture is the
            // screen rather than the top of a form.
            SessionChrome(
                // The chrome is laid over the whole picture: without a size of its
                // own it is anchored to its own content, which puts a rail that
                // should float in the corner of the screen at the corner of a
                // 54dp box — seen on the phone as a pill hanging off the edge.
                modifier = Modifier.fillMaxSize(),
                open = railOpen,
                mouse = mouseOpen,
                settings = settingsOpen,
                keyboard = keyboardShown,
                quality = quality,
                mode = mode,
                codec = codec,
                onOpen = { railOpen = it },
                onMouse = { mouseOpen = it },
                onSettings = { settingsOpen = it },
                onKeyboard = { keyboardShown = !keyboardShown },
                onLeave = onLeave,
                onScroll = { notches ->
                    val (x, y) = surface.cursor
                        ?: hello?.let { it.width / 2 to it.height / 2 }
                        ?: (0 to 0)
                    socket.wheel(x, y, notches)
                },
                onMode = { wanted ->
                    mode = wanted
                    socket.keepInputMode(wanted.stored)
                },
                onCodec = { wanted ->
                    codec = wanted
                    socket.settings(codec = wanted)
                    socket.keepCodec(wanted)
                },
                onQuality = { value ->
                    quality = value
                    socket.settings(quality = value)
                },
                onQualitySettled = { socket.keepQuality(quality) },
                onFit = {
                    if (view?.isActualSize() == true) view?.fitScreen() else view?.actualSize()
                },
                onHold = { button -> view?.holdButton(button) },
                onRelease = { view?.releaseButton() },
            )
        }
        if (keyboardShown) {
            KeyboardBar(socket)
        }
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
 * How long a click gets to move the machine's pointer.
 *
 * A tap is sent to the machine, and what the machine puts under its pointer is
 * what says whether the keyboard belongs on screen. Asking straight away would
 * answer about the place it was clicked from rather than the place it was
 * clicked on: a quarter of a second is longer than the round trip and shorter
 * than a person's next tap.
 */
private const val KEYBOARD_SETTLE_MS = 250L
