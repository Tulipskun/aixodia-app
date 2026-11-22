package com.tulipskun.aixodia.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** The pictures the rail offers, drawn rather than borrowed from a font. */
internal enum class Glyph { MENU, KEYBOARD, MOUSE, TUNE, CLOSE }

/**
 * The session's chrome, shaped like the viewer this replaces.
 *
 * RealVNC's session screen has no bars: the desktop is the screen, and the only
 * thing on top of it is a small floating button — three dots — which a thumb can
 * drag anywhere and which opens into a black pill of icon buttons. The mouse
 * buttons live in a second small pane that the mouse item summons, because on a
 * phone the right button has no other way to exist.
 *
 * This is that arrangement and nothing more: a rail at the bottom right, a pill
 * of the four things a session needs (keyboard, mouse, settings, close), and no
 * permanent furniture over the picture. The keyboard's own bar and the buttons
 * it raises belong to the panel, which owns the socket; here they are asked for
 * by name.
 */
@Composable
fun SessionChrome(
    open: Boolean,
    mouse: Boolean,
    settings: Boolean,
    keyboard: Boolean,
    quality: Int,
    mode: InputMode,
    codec: String,
    onOpen: (Boolean) -> Unit,
    onMouse: (Boolean) -> Unit,
    onSettings: (Boolean) -> Unit,
    onKeyboard: () -> Unit,
    onLeave: () -> Unit,
    onScroll: (Int) -> Unit,
    onMode: (InputMode) -> Unit,
    onCodec: (String) -> Unit,
    onQuality: (Int) -> Unit,
    onQualitySettled: () -> Unit,
    onFit: () -> Unit,
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rail = with(density) { SessionLayout.RAIL_DP.dp.toPx() }
    val margin = with(density) { SessionLayout.MARGIN_DP.dp.toPx() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Kept across a rotation: a reader who moved the rail expects it back where
    // it was, and the clamp below brings it home if the screen got smaller.
    var at by rememberSaveable { mutableStateOf<Pair<Float, Float>?>(null) }

    Box(
        modifier = modifier.onSizeChanged { size ->
            box = size
            at = at?.let { (x, y) ->
                SessionLayout.clamp(x, y, rail, size.width.toFloat(), size.height.toFloat(), margin)
            } ?: SessionLayout.home(rail, size.width.toFloat(), size.height.toFloat(), margin)
        },
    ) {
        val home = SessionLayout.home(rail, box.width.toFloat(), box.height.toFloat(), margin)
        val parked = at ?: return@Box
        // An open pane needs room the collapsed button does not: it is wider than
        // the button and it opens upward, so a rail near the top-left corner puts
        // half of it off the screen. While a pane is open the rail therefore sits
        // where the pane fits, and goes back where the reader parked it when the
        // pane closes.
        val panesOpen = open || mouse || settings
        val (x, y) = if (panesOpen) {
            val pill = with(density) { SessionLayout.PILL_DP.dp.toPx() }
            SessionLayout.openAt(
                x = parked.first,
                y = parked.second,
                rail = rail,
                pill = pill,
                width = box.width.toFloat(),
                height = box.height.toFloat(),
                margin = margin,
            )
        } else {
            parked
        }

        // One column, right-aligned and bottom-anchored: the panes grow upward
        // from the rail, so opening one never pushes the rail off the screen.
        // The drag moves the whole column, and only the rail's own bounds are
        // clamped — a pane is allowed to hang over the picture, which is what
        // makes it a pane and not a layout.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset {
                    IntOffset((x - home.first).roundToInt(), (y - home.second).roundToInt())
                }
                .padding(end = SessionLayout.MARGIN_DP.dp, bottom = SessionLayout.MARGIN_DP.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (settings) {
                SettingsPane(
                    quality = quality,
                    mode = mode,
                    codec = codec,
                    onMode = onMode,
                    onCodec = onCodec,
                    onQuality = onQuality,
                    onQualitySettled = onQualitySettled,
                    onFit = onFit,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (mouse) {
                MousePane(
                    onHold = onHold,
                    onRelease = onRelease,
                    onScroll = onScroll,
                    onDismiss = { onMouse(false) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Rail(
                open = open,
                keyboard = keyboard,
                mouse = mouse,
                settings = settings,
                onOpen = onOpen,
                onMouse = onMouse,
                onSettings = onSettings,
                onKeyboard = onKeyboard,
                onLeave = onLeave,
                modifier = Modifier.dragged(
                    at = { at },
                    move = { nx, ny ->
                        at = SessionLayout.clamp(nx, ny, rail, box.width.toFloat(), box.height.toFloat(), margin)
                    },
                ),
            )
        }
    }
}

/**
 * The rail: the collapsed button, or the pill it opens into.
 *
 * Both live in the same place and only one is drawn, which is what makes the
 * button appear to grow into a toolbar rather than be joined by one.
 */
@Composable
private fun Rail(
    open: Boolean,
    keyboard: Boolean,
    mouse: Boolean,
    settings: Boolean,
    onOpen: (Boolean) -> Unit,
    onMouse: (Boolean) -> Unit,
    onSettings: (Boolean) -> Unit,
    onKeyboard: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!open) {
        Box(
            modifier = modifier
                .size(SessionLayout.RAIL_DP.dp)
                .background(Color(SessionLayout.PILL_COLOUR), CircleShape)
                .border(SessionLayout.STROKE_DP.dp, Color(SessionLayout.EDGE_COLOUR), CircleShape)
                .tapped("Show controls") { onOpen(true) },
            contentAlignment = Alignment.Center,
        ) {
            Drawn(Glyph.MENU, Color.White)
        }
        return
    }

    Row(
        modifier = modifier
            .width(SessionLayout.PILL_DP.dp)
            .height(SessionLayout.RAIL_DP.dp)
            .background(Color(SessionLayout.PILL_COLOUR), RoundedCornerShape(percent = 50))
            .border(
                SessionLayout.STROKE_DP.dp,
                Color(SessionLayout.EDGE_COLOUR),
                RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Item(Glyph.KEYBOARD, "Keyboard", active = keyboard, onTap = onKeyboard)
        Item(Glyph.MOUSE, "Mouse buttons", active = mouse) { onMouse(!mouse) }
        Item(Glyph.TUNE, "Settings", active = settings) { onSettings(!settings) }
        Item(Glyph.CLOSE, "Close", tint = Color(SessionLayout.LEAVE_COLOUR)) { onLeave() }
    }
}

/** One 44dp icon in the pill; blue behind it while the pane it opens is open. */
@Composable
private fun Item(
    glyph: Glyph,
    label: String,
    active: Boolean = false,
    tint: Color = Color.White,
    onTap: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SessionLayout.ITEM_DP.dp)
            .background(
                if (active) Color(SessionLayout.ACTIVE_COLOUR) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .tapped(label, onTap),
        contentAlignment = Alignment.Center,
    ) {
        Drawn(glyph, tint)
    }
}

/**
 * The mouse pane: the three buttons a phone does not have, and a wheel.
 *
 * Left, middle and right are held, not clicked: holding one and dragging with
 * the other finger is how a window is carried, which a tap cannot do. The wheel
 * sends a notch per 24dp a finger travels, so a list can be scrolled without
 * hunting for a scrollbar.
 */
@Composable
private fun MousePane(
    onHold: (String) -> Unit,
    onRelease: () -> Unit,
    onScroll: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pane = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .background(Color(0xE6000000), pane)
            .border(1.dp, Color(SessionLayout.EDGE_COLOUR), pane)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wheel(onScroll)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (button in PointerPlan.BUTTONS) {
                Box(
                    modifier = Modifier
                        .size(SessionLayout.ITEM_DP.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                        .held(onHold = { onHold(button) }, onRelease = onRelease),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(PointerPlan.label(button), color = Color.White, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(SessionLayout.ITEM_DP.dp * 3 + 12.dp)
                .height(26.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .tapped("Hide mouse buttons", onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Drawn(Glyph.CLOSE, Color(SessionLayout.EDGE_COLOUR), size = 14.dp)
        }
    }
}

/** The wheel: a drag becomes notches, and the remainder is never dropped. */
@Composable
private fun Wheel(onScroll: (Int) -> Unit) {
    val step = with(LocalDensity.current) { WHEEL_STEP.toPx() }
    var owed by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .width(SessionLayout.ITEM_DP.dp * 3 + 12.dp)
            .height(40.dp)
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .pointerInput(step) {
                detectDragGestures { change, drag ->
                    change.consume()
                    owed += drag.y
                    while (owed <= -step) {
                        owed += step
                        onScroll(-1)
                    }
                    while (owed >= step) {
                        owed -= step
                        onScroll(1)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val arrow = Color(SessionLayout.EDGE_COLOUR)
        Canvas(Modifier.size(16.dp)) {
            val w = size.width
            val h = size.height
            val stroke = 1.6f
            drawLine(arrow, Offset(w / 2, 1f), Offset(w * 0.3f, h * 0.42f), stroke, StrokeCap.Round)
            drawLine(arrow, Offset(w / 2, 1f), Offset(w * 0.7f, h * 0.42f), stroke, StrokeCap.Round)
            drawLine(arrow, Offset(w / 2, h - 1f), Offset(w * 0.3f, h * 0.58f), stroke, StrokeCap.Round)
            drawLine(arrow, Offset(w / 2, h - 1f), Offset(w * 0.7f, h * 0.58f), stroke, StrokeCap.Round)
        }
    }
}

/** How far a finger travels for one notch of the wheel. */
private val WHEEL_STEP = 24.dp

/**
 * The pane the settings item opens: the two choices a viewer has that the rail
 * has no room for — how a finger becomes a pointer, and how the picture is
 * carried — plus the sharpness, which needs a slider to be adjustable at all.
 */
@Composable
private fun SettingsPane(
    quality: Int,
    mode: InputMode,
    codec: String,
    onMode: (InputMode) -> Unit,
    onCodec: (String) -> Unit,
    onQuality: (Int) -> Unit,
    onQualitySettled: () -> Unit,
    onFit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pane = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .width(SessionLayout.PILL_DP.dp)
            .background(Color(0xE6000000), pane)
            .border(1.dp, Color(SessionLayout.EDGE_COLOUR), pane)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        // Two rows rather than one scrolled row: five choices in one line are
        // narrower than the pill, and a choice that has to be found by scrolling
        // is a choice that is not there.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice(mode == InputMode.TRACKPAD, "trackpad") { onMode(InputMode.TRACKPAD) }
            Choice(mode == InputMode.DIRECT, "touch") { onMode(InputMode.DIRECT) }
            Choice(false, "fit · 1:1", onTap = onFit)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice(codec == "h264", "h264") { onCodec("h264") }
            Choice(codec != "h264", "jpeg") { onCodec("jpeg") }
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$quality%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            Slider(
                value = quality.toFloat(),
                onValueChange = { onQuality(it.roundToInt()) },
                onValueChangeFinished = onQualitySettled,
                valueRange = 10f..95f,
                // The viewer's own blue, not the theme's violet: the rail is one
                // object and its parts must agree.
                colors = SliderDefaults.colors(
                    thumbColor = Color(SessionLayout.ACTIVE_COLOUR),
                    activeTrackColor = Color(SessionLayout.ACTIVE_COLOUR),
                    inactiveTrackColor = Color(0xFF1E1E1E),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Choice(selected: Boolean, label: String, onTap: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (selected) Color.White else Color(SessionLayout.EDGE_COLOUR),
        modifier = Modifier
            .background(
                if (selected) Color(SessionLayout.ACTIVE_COLOUR) else Color(0xFF1E1E1E),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .tapped(label, onTap),
    )
}

/** A glyph at 22dp, drawn with lines so no font can change its proportions. */
@Composable
private fun Drawn(glyph: Glyph, tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val thin = h * 0.075f
        when (glyph) {
            Glyph.MENU -> {
                val r = this.size.minDimension * 0.075f
                for (dx in listOf(0.25f, 0.5f, 0.75f)) {
                    drawCircle(tint, r, Offset(w * dx, h / 2))
                }
            }
            Glyph.KEYBOARD -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.04f, h * 0.2f),
                    size = Size(w * 0.92f, h * 0.6f),
                    cornerRadius = CornerRadius(h * 0.12f),
                    style = Stroke(thin),
                )
                for (row in 0..1) {
                    for (col in 0..3) {
                        drawCircle(tint, h * 0.05f, Offset(w * (0.2f + col * 0.2f), h * (0.37f + row * 0.15f)))
                    }
                }
                drawLine(tint, Offset(w * 0.3f, h * 0.68f), Offset(w * 0.7f, h * 0.68f), thin, StrokeCap.Round)
            }
            Glyph.MOUSE -> {
                drawRoundRect(
                    tint,
                    topLeft = Offset(w * 0.27f, h * 0.06f),
                    size = Size(w * 0.46f, h * 0.88f),
                    cornerRadius = CornerRadius(w * 0.23f),
                    style = Stroke(thin),
                )
                drawLine(tint, Offset(w * 0.27f, h * 0.38f), Offset(w * 0.73f, h * 0.38f), thin)
                drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.3f), thin, StrokeCap.Round)
            }
            Glyph.TUNE -> {
                val knobs = listOf(0.28f to 0.35f, 0.5f to 0.65f, 0.72f to 0.45f)
                for ((y, x) in knobs) {
                    drawLine(tint, Offset(w * 0.12f, h * y), Offset(w * 0.88f, h * y), thin, StrokeCap.Round)
                    drawCircle(tint, h * 0.1f, Offset(w * x, h * y))
                }
            }
            Glyph.CLOSE -> {
                val pad = this.size.minDimension * 0.22f
                val max = this.size.minDimension - pad
                val stroke = this.size.minDimension * 0.09f
                drawLine(tint, Offset(pad, pad), Offset(max, max), stroke, StrokeCap.Round)
                drawLine(tint, Offset(max, pad), Offset(pad, max), stroke, StrokeCap.Round)
            }
        }
    }
}

/** A tap that goes straight through: the rail is a tool, not a trade button. */
@Composable
private fun Modifier.tapped(label: String, onTap: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .semantics { contentDescription = label }
        .clickable(interactionSource = source, indication = null, onClick = onTap)
}

/** Press-and-hold, with the release guaranteed even if the finger leaves. */
@Composable
private fun Modifier.held(onHold: () -> Unit, onRelease: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press) continue
                onHold()
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
    }

/** Drags the rail by the finger, in whole-screen coordinates. */
@Composable
private fun Modifier.dragged(at: () -> Pair<Float, Float>?, move: (Float, Float) -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectDragGestures { change, drag ->
            change.consume()
            val (x, y) = at() ?: return@detectDragGestures
            move(x + drag.x, y + drag.y)
        }
    }
