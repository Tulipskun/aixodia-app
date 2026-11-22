package com.tulipskun.aixodia.ui.screen

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.tulipskun.aixodia.data.remote.ScreenSocket
import com.tulipskun.aixodia.data.remote.ScrollAxis
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Path

/**
 * The picture, kept as the last bitmap that arrived.
 *
 * Tiles are painted straight onto one bitmap the size of the captured screen, so
 * a repaint is one draw call and a tile costs one `drawBitmap` at its offset. The
 * mosaic is never rebuilt from scratch: the engine only sends what changed, and
 * what did not change is still here.
 *
 * [blank] is the case the engine cannot help with: no frame has arrived yet, or
 * the link dropped before one did. Drawing black and drawing "no picture" look
 * identical on a phone, so the view is told which it is and says so.
 */
class ScreenSurface {
    @Volatile
    private var frame: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = true }

    /** True until a frame has painted something. */
    @Volatile
    var blank: Boolean = true
        private set

    fun sizeTo(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val current = frame
        if (current != null && current.width == width && current.height == height) return
        frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            // A new size has nothing to keep: the whole screen is coming.
            it.eraseColor(Color.BLACK)
        }
        blank = true
    }

    /** Paints one frame's changed tiles into the picture. */
    fun paint(patch: ScreenSocket.Event.Patch) {
        val target = frame ?: run {
            sizeTo(patch.width, patch.height)
            frame
        } ?: return
        val canvas = Canvas(target)
        for (tile in patch.patches) {
            val destination = tileRect(tile.col, tile.row, tile.cell, tile.bitmap.width, tile.bitmap.height)
            canvas.drawBitmap(tile.bitmap, null, destination, paint)
        }
        if (patch.patches.isNotEmpty()) blank = false
    }

    companion object Place {
        /**
         * Where a tile's pixels land in the picture.
         *
         * The place comes from the grid, not from the tile's own size: a tile
         * against the right or bottom edge is clipped to what is left of the
         * picture, so it is smaller than its cell. Measuring by the bitmap would
         * pull such a tile toward the origin — a 720-high picture with 64-pixel
         * tiles leaves a last row 16 high, which would be drawn at 176 instead
         * of 704 — and the real edge would never be painted at all.
         */
        fun tileRect(col: Int, row: Int, cell: Int, width: Int, height: Int): RectF {
            val left = (col * cell).toFloat()
            val top = (row * cell).toFloat()
            return RectF(left, top, left + width, top + height)
        }
    }

    /**
     * Where the machine's pointer is drawn, in captured pixels, and the last
     * place the local finger left it.
     *
     * A remote desktop without a visible pointer is a guess: you move your
     * finger and hope the machine agrees. This is the arrow, drawn here.
     */
    @Volatile
    var cursor: Pair<Int, Int>? = null
        private set

    fun cursorTo(at: Pair<Int, Int>?) {
        cursor = at
    }

    /** Forgets the picture, for a link that has gone away. */
    fun clear() {
        frame = null
        blank = true
    }

    fun bitmap(): Bitmap? = frame
}

/**
 * The view that draws the picture and turns a finger into a mouse.
 *
 * Zoom and pan work the way a phone's image viewer does, and the way the VNC
 * viewer everyone knows does: one finger drags the pointer when the picture fits
 * the screen, and pans the picture when it does not; two fingers pinch to zoom;
 * a double tap fits the picture or returns to actual size. Everything is
 * anchored on the point between the fingers, so a pinch grows the part being
 * looked at rather than the middle of the screen.
 *
 * Which of two ways a finger moves the pointer is the reader's choice ([inputMode]),
 * the same one the VNC viewers offer: the pad, where the arrow moves with the
 * finger and a tap clicks under the arrow, or the screen itself, where a touch
 * lands where it is put and a drag drags what is under it. Both keep the
 * gestures a remote desktop needs and a photo viewer does not: a tap is a click,
 * two fingers moving together scroll instead of zooming, and a button held from
 * the panel turns a one-finger drag into a window being carried.
 */
@SuppressLint("ViewConstructor")
class ScreenView(context: Context) : View(context) {
    private var socket: ScreenSocket? = null
    private var surface: ScreenSurface? = null

    private val draw = Paint().apply {
        isAntiAlias = true
        // The screen is not a photo: nearest-neighbour keeps text legible when
        // the phone shows a smaller picture than the engine captured.
        isFilterBitmap = false
    }
    private val background = Paint().apply { color = Color.BLACK }
    private val cursorEdge = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.BLACK
    }
    private val cursorFill = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    /** 1 means the picture exactly fits the view. */
    private var zoom = 1f
    /** Offset of the picture within the view, in view pixels, after the fit. */
    private var panX = 0f
    private var panY = 0f

    private var downAt = 0L
    private var downX = 0f
    private var downY = 0f
    /** Where the press began, in picture pixels. */
    private var downPx = 0
    private var downPy = 0
    private var lastX = 0f
    private var lastY = 0f
    private var dragged = false
    /** True while a double tap is holding the left button down. */
    private var held = false
    /** True once a two-finger gesture has spread, so it is a pinch not a scroll. */
    private var pinched = false
    /** True once a two-finger gesture has scrolled the machine. */
    private var scrolled = false
    private var movedEnough = false
    private var fingersDown = 0
    /** How many fingers the touch began with, which decides what a click means. */
    private var fingersAtStart = 0
    /** The most fingers down at once during this touch. */
    private var maxFingers = 0
    /** When the last tap ended, so a second one can be recognised. */
    private var lastTapAt = 0L
    /** True when this touch is the second tap of a double tap. */
    private var secondTap = false
    private var scrollFromY = 0f
    private var scrollFromX = 0f
    /** True once a second finger has turned the gesture into picture handling. */
    private var twoFinger = false
    /** Where two fingers started, so their movement can be measured. */
    private var twoFingerX = 0f
    private var twoFingerY = 0f
    /** Movement a two-finger drag has made but not yet spent on a notch. */
    private var owedScrollX = 0f
    private var owedScrollY = 0f

    /**
     * Where the arrow is while a finger is moving it, in picture pixels.
     *
     * The machine sends its pointer back with every frame, but that echo is a
     * frame old. Adding a movement to it — as this view used to — makes the
     * arrow's travel depend on how fast the picture is arriving: a slow drag
     * moved it 0.3 of the finger's travel and a fast one 2.8 times as far, which
     * is neither predictable nor the same as RealVNC. The arrow therefore has a
     * position of its own for the length of a touch, seeded from the machine
     * when the touch begins, and the machine's echoes are left to draw it.
     */
    private var pointerX = -1
    private var pointerY = -1
    /** Movement owed to the arrow but not yet worth a whole pixel. */
    private var owedMoveX = 0f
    private var owedMoveY = 0f
    /** The finger's speed, smoothed, in view pixels per millisecond. */
    private var moveSpeed = 0f
    private var lastMoveAt = 0L

    /**
     * True when the picture is a decoded video underneath this view rather than
     * a bitmap drawn by it.
     *
     * The view still owns the fingers and still draws the arrow; it only stops
     * painting a picture it does not have.
     */
    var videoMode: Boolean = false

    /**
     * Whether a finger pushes the pointer or is the pointer.
     *
     * The pad is the default because it is the mode that works on a desktop too
     * big for the phone; touch mode is for one that already fits.
     */
    var inputMode: InputMode = InputMode.TRACKPAD
        set(value) {
            if (value == field) return
            field = value
            // A press the other mode left down would stay down: touch mode leaves
            // the button held for the whole touch, and the pad never touches it.
            if (value == InputMode.TRACKPAD) cancelTouchPress()
        }

    /** True while a touch in touch mode has the machine's button pressed. */
    private var touchPressed = false

    /** The button the panel is holding down, if any. */
    private var heldButton: String? = null

    /** When the last pointer move was sent, so they are not sent faster than frames. */
    private var lastMoveSent = 0L
    /** True when the newest position has been sent, false while it is only owed. */
    private var pointerSettled = true
    /** The move that is owed, kept as a closure so the newest one wins. */
    private var settleMove: (() -> Unit)? = null
    private val settleRun = Runnable {
        if (!pointerSettled) {
            settleMove?.invoke()
            pointerSettled = true
            lastMoveSent = System.currentTimeMillis()
        }
    }

    // Pinch state, captured when the second finger lands.
    private var pinchStartDistance = 0f
    private var pinchStartZoom = 1f
    private var pinchFocusX = 0f
    private var pinchFocusY = 0f
    private var pinchStartPanX = 0f
    private var pinchStartPanY = 0f


    fun attach(socket: ScreenSocket, surface: ScreenSurface) {
        this.socket = socket
        this.surface = surface
    }

    /**
     * Holds a mouse button down, wherever the pointer is.
     *
     * This is the viewer's mouse-button panel: press it, then drag with one
     * finger to carry the window; let go to drop it. Without it a phone cannot
     * move a window at all, because a drag needs a button held for longer than a
     * tap lasts.
     */
    fun holdButton(button: String) {
        val socket = socket ?: return
        val at = arrow() ?: return
        heldButton = button
        socket.down(at.first, at.second, button)
    }

    /** Lets go of a button the panel is holding, wherever the pointer ended up. */
    fun releaseButton() {
        val socket = socket ?: return
        val button = heldButton ?: return
        val at = arrow()
        heldButton = null
        at?.let { socket.up(it.first, it.second, button) }
    }

    /** Whether the panel is holding a button down right now. */
    fun isHoldingButton(): Boolean = heldButton != null

    /** Whether the picture is shown one pixel per pixel already. */
    fun isActualSize(): Boolean {
        val picture = surface?.bitmap() ?: return false
        if (picture.width <= 0 || picture.height <= 0 || width == 0 || height == 0) return false
        val fit = min(width.toFloat() / picture.width, height.toFloat() / picture.height)
        if (fit <= 0f) return false
        return zoom >= (1f / fit).coerceIn(MIN_ZOOM, MAX_ZOOM) - 0.01f
    }

    /** Scale the picture to fit the view: the whole desktop, small. */
    fun fitScreen() {
        zoom = 1f
        panX = 0f
        panY = 0f
        postInvalidateOnAnimation()
    }

    /**
     * One picture pixel per screen pixel.
     *
     * A fitted 1920-wide desktop on a 1080-wide phone is at 56%, where small text
     * is unreadable; this is the reader saying "show it real" and panning around.
     */
    fun actualSize() {
        val picture = surface?.bitmap() ?: return
        if (picture.width <= 0 || picture.height <= 0 || width == 0 || height == 0) return
        val fit = min(width.toFloat() / picture.width, height.toFloat() / picture.height)
        if (fit <= 0f) return
        zoom = (1f / fit).coerceIn(MIN_ZOOM, MAX_ZOOM)
        panX = 0f
        panY = 0f
        clampPan()
        postInvalidateOnAnimation()
    }

    /** Puts a fresh frame on screen. Called from the socket's collector. */
    fun invalidateFrame() {
        postInvalidateOnAnimation()
    }

    /** Drops the picture and goes back to "nothing yet". */
    fun cleared() {
        zoom = 1f
        panX = 0f
        panY = 0f
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)
        if (videoMode) {
            // The picture is on the video surface beneath; only the arrow and
            // the gestures belong to this view.
            val picture = surface?.bitmap()
            if (picture != null) {
                drawCursor(canvas, picture.width, picture.height, pictureRect(picture.width, picture.height))
            }
            return
        }
        if (surface?.blank != false) return
        val picture = surface?.bitmap() ?: return
        if (picture.width == 0 || picture.height == 0) return
        val box = pictureRect(picture.width, picture.height)
        canvas.drawBitmap(picture, null, box, draw)
        drawCursor(canvas, picture.width, picture.height, box)
    }

    /**
     * The arrow, at the pointer's place in the picture, scaled with it.
     *
     * Drawn after the tiles so it is never buried, and with a dark outline under
     * a light fill so it stays visible over both a white document and a black
     * terminal.
     */
    private fun drawCursor(canvas: Canvas, pictureWidth: Int, pictureHeight: Int, box: RectF) {
        val at = surface?.cursor ?: return
        if (pictureWidth <= 0 || pictureHeight <= 0 || box.width() <= 0f) return
        val scaleX = box.width() / pictureWidth
        val scaleY = box.height() / pictureHeight
        val x = box.left + at.first * scaleX
        val y = box.top + at.second * scaleY
        // A pointer that shrinks to nothing on a fitted picture is unreadable;
        // it keeps a floor size of its own, as the desktop viewers do.
        val size = (CURSOR_SIZE * max(1f, min(scaleX, scaleY))).coerceAtMost(CURSOR_MAX)
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x + size * 0.62f, y + size)
            lineTo(x + size * 0.28f, y + size * 0.76f)
            lineTo(x + size * 0.08f, y + size * 1.12f)
            lineTo(x - size * 0.10f, y + size * 1.02f)
            lineTo(x + size * 0.10f, y + size * 0.66f)
            lineTo(x - size * 0.16f, y + size * 0.66f)
            close()
        }
        canvas.drawPath(path, cursorEdge)
        canvas.drawPath(path, cursorFill)
    }

    /** Where the picture is drawn, after fit, zoom and pan. */
    private fun pictureRect(pictureWidth: Int, pictureHeight: Int): RectF {
        if (pictureWidth <= 0 || pictureHeight <= 0 || width == 0 || height == 0) {
            return RectF(0f, 0f, width.toFloat(), height.toFloat())
        }
        val fitScale = min(width.toFloat() / pictureWidth, height.toFloat() / pictureHeight)
        val scale = fitScale * zoom
        val drawnWidth = pictureWidth * scale
        val drawnHeight = pictureHeight * scale
        val left = (width - drawnWidth) / 2f + panX
        val top = (height - drawnHeight) / 2f + panY
        return RectF(left, top, left + drawnWidth, top + drawnHeight)
    }

    /**
     * A point on the view, as a point on the captured screen.
     *
     * Outside the picture is clamped rather than dropped: a finger that leaves
     * the letterbox during a drag should keep dragging along the nearest edge,
     * the way a mouse at the edge of a screen does.
     */
    private fun toScreen(x: Float, y: Float): Pair<Int, Int>? {
        // The picture's size comes from the frame, not from the view: turning a
        // touch into a pointer position means undoing exactly what drawing did
        // — the fit, the zoom and the pan — so the two must share one rectangle.
        val picture = surface?.bitmap() ?: return null
        if (picture.width <= 0 || picture.height <= 0) return null
        val box = pictureRect(picture.width, picture.height)
        if (box.width() <= 0f || box.height() <= 0f) return null
        val scaleX = picture.width / box.width()
        val scaleY = picture.height / box.height()
        val px = ((x - box.left) * scaleX).roundToInt().coerceIn(0, picture.width - 1)
        val py = ((y - box.top) * scaleY).roundToInt().coerceIn(0, picture.height - 1)
        return px to py
    }

    /**
     * The pointer's place, in picture pixels, under the finger.
     *
     * A phone touch screen is used as the machine's screen, the way RealVNC does
     * it: the arrow goes exactly where the finger is, unreached by any gain or
     * offset. Anything else makes the arrow land somewhere the finger is not,
     * which reads as "the position is wrong" — because it is.
     */
    private fun pointerFor(event: MotionEvent): Pair<Int, Int>? = toScreen(event.x, event.y)

    /** Keeps the picture from being dragged off into empty space. */
    private fun clampPan() {
        val picture = surface?.bitmap() ?: return
        val box = pictureRect(picture.width, picture.height)
        // How far it may slide: half the overflow on each side, so an edge can
        // reach the middle of the view but not fly past it.
        val slackX = max(0f, (box.width() - width) / 2f)
        val slackY = max(0f, (box.height() - height) / 2f)
        panX = panX.coerceIn(-slackX, slackX)
        panY = panY.coerceIn(-slackY, slackY)
    }

    private fun distance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.hypot(dx, dy)
    }

    private fun focusX(event: MotionEvent): Float =
        if (event.pointerCount < 2) event.x else (event.getX(0) + event.getX(1)) / 2f

    private fun focusY(event: MotionEvent): Float =
        if (event.pointerCount < 2) event.y else (event.getY(0) + event.getY(1)) / 2f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val socket = socket ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downAt = System.currentTimeMillis()
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragged = false
                pinched = false
                movedEnough = false
                // How many fingers started the touch decides the click, and it is
                // remembered here because the count at the end is already one
                // fewer by the time the last finger lifts.
                fingersDown = 1
                fingersAtStart = 1
                maxFingers = 1
                touchPressed = false
                // The arrow keeps the position it had. The machine's opinion of
                // where it is arrives with the picture, and the picture only
                // repaints when pixels change — a moving pointer changes none —
                // so that opinion is often a whole drag out of date. It is
                // believed once, when there is nothing better.
                if (pointerX < 0 || pointerY < 0) {
                    surface?.cursor?.let { (x, y) ->
                        pointerX = x
                        pointerY = y
                    }
                }
                owedMoveX = 0f
                owedMoveY = 0f
                moveSpeed = 0f
                lastMoveAt = event.eventTime
                // A second tap that lands soon after the first is the start of a
                // double tap, which in this viewer is click-and-hold.
                secondTap = downAt - lastTapAt <= DOUBLE_TAP_MS
                lastTapAt = downAt
                // Touch mode is the screen itself: the press happens where the
                // finger lands, and it stays pressed until the finger leaves, so
                // a slow movement is a drag and a quick one is a click.
                if (inputMode == InputMode.DIRECT) {
                    pressDirect(event)
                } else {
                    // The pad's arrow follows the finger relatively, so a touch
                    // can land on top of it. RealVNC keeps the cursor "offset
                    // from under your finger so you can always see it"; the same
                    // rule here, applied once when the gesture starts rather than
                    // during it, because an arrow that keeps fleeing a moving
                    // thumb is harder to aim than one that is briefly hidden.
                    keepArrowVisible(event)
                }
                // A tap is remembered so a second one can turn it into a double.
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                fingersDown = event.pointerCount
                maxFingers = maxOf(maxFingers, event.pointerCount)
                dragged = false
                // A second finger means the picture, not the machine: a press
                // touch mode had already put down is let go rather than left held
                // through a pinch.
                cancelTouchPress()
                if (fingersDown >= 2) {
                    // Two fingers own the picture; the movement done with one is
                    // over, and it never pressed anything — a drag that begins
                    // with a tap is the double-tap-and-drag gesture, not a move.
                    pinchStartDistance = distance(event)
                    pinchStartZoom = zoom
                    pinchFocusX = focusX(event)
                    pinchFocusY = focusY(event)
                    pinchStartPanX = panX
                    pinchStartPanY = panY
                    twoFingerX = focusX(event)
                    twoFingerY = focusY(event)
                    scrollFromY = focusY(event)
                    scrollFromX = focusX(event)
                    owedScrollX = 0f
                    owedScrollY = 0f
                    scrolled = false
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (fingersDown >= 2) {
                    moveTwoFingers(event)
                    return true
                }
                if (touchPressed) {
                    moveDirect(event)
                    return true
                }
                moveOneFinger(event)
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // One finger of a several-finger touch left. The count is left as
                // it was on purpose: lowering it would let the ACTION_UP that
                // follows look like a one-finger tap, which turned a two-finger
                // click into a left click and a three-finger click into nothing.
                dragged = false
                movedEnough = true
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (touchPressed) releaseTouchPress(event) else endOneFinger(event)
                fingersDown = 0
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                if (held) {
                    surface?.cursor?.let { (x, y) -> socket.up(x, y, "left") }
                    held = false
                }
                cancelTouchPress()
                dragged = false
                pinched = false
                fingersDown = 0
                return true
            }
        }
        return true
    }

    /**
     * The press that starts a touch-mode gesture.
     *
     * Placed under the finger, not at the arrow: in this mode the picture is the
     * screen, so the touch is the position. The button is the one chosen in the
     * bar, because a second finger here is a pinch rather than a right-click.
     */
    private fun pressDirect(event: MotionEvent) {
        val socket = socket ?: return
        val at = pointerFor(event) ?: return
        touchPressed = true
        pointerX = at.first
        pointerY = at.second
        markCursor(at.first, at.second)
        socket.down(at.first, at.second, "left")
    }

    /** A touch-mode drag: the pointer follows the finger exactly. */
    private fun moveDirect(event: MotionEvent) {
        val socket = socket ?: return
        val at = pointerFor(event) ?: return
        if (abs(event.x - downX) > TOUCH_SLOP || abs(event.y - downY) > TOUCH_SLOP) {
            movedEnough = true
        }
        pointerX = at.first
        pointerY = at.second
        markCursor(at.first, at.second)
        sendMove(socket, at.first, at.second)
    }

    /** Ends a touch-mode press where the finger left, not where it began. */
    private fun releaseTouchPress(event: MotionEvent) {
        val socket = socket ?: return
        touchPressed = false
        pointerSettled = true
        removeCallbacks(settleRun)
        val at = pointerFor(event) ?: surface?.cursor ?: return
        socket.up(at.first, at.second, "left")
    }

    /** Lets go of a press that a pinch or a mode change interrupted. */
    private fun cancelTouchPress() {
        if (!touchPressed) return
        touchPressed = false
        pointerSettled = true
        removeCallbacks(settleRun)
        val socket = socket ?: return
        surface?.cursor?.let { (x, y) -> socket.up(x, y, "left") }
    }

    /**
     * Sends a pointer move, at most once a frame.
     *
     * A held drag reports motion at the touch panel's rate, not the machine's,
     * and a queue of positions the pointer has already left only adds lag to the
     * window being dragged. The newest position is always the one sent.
     */
    private fun sendMove(socket: ScreenSocket, x: Int, y: Int) {
        val now = System.currentTimeMillis()
        if (now - lastMoveSent >= MOVE_INTERVAL_MS) {
            socket.move(x, y)
            lastMoveSent = now
            pointerSettled = true
        } else {
            pointerSettled = false
            settleMove = { socket.move(x, y) }
            postDelayed(settleRun, MOVE_INTERVAL_MS)
        }
    }

    /**
     * One finger drags: the pointer moves by part of how far the finger moved.
     *
     * The picture is used as a drag pad, not as a touch screen. That is what the
     * viewer's own code does, and it is worth being exact about where it shows:
     * its pointer arithmetic is `new f(from, to, scale)` whose body is
     * `from / scale - to / scale` — a difference of two positions, i.e. a
     * movement — and that movement is then turned into a screen point by adding
     * it to the current view offset and clamping it to the desktop. Placing the
     * pointer at the touch instead would make it jump under the finger and would
     * lose the whole desktop the moment the finger leaves the picture.
     *
     * How much of the movement is taken is [PointerSpeed]'s question, and the
     * answer grows with how fast the finger is going: slow for aiming, fast for
     * crossing the screen. The remainder is carried, so a crawl still creeps.
     */
    private fun moveOneFinger(event: MotionEvent) {
        val socket = socket ?: return
        val picture = surface?.bitmap() ?: return
        if (picture.width <= 0 || picture.height <= 0) return
        val box = pictureRect(picture.width, picture.height)
        if (box.width() <= 0f || box.height() <= 0f) return
        // How much of the machine one pixel of view covers, so a movement of the
        // finger becomes an equal-looking movement of the arrow.
        val scaleX = picture.width / box.width()
        val scaleY = picture.height / box.height()

        if (abs(event.x - downX) > TOUCH_SLOP || abs(event.y - downY) > TOUCH_SLOP) {
            movedEnough = true
        }

        val dxView = event.x - lastX
        val dyView = event.y - lastY
        lastX = event.x
        lastY = event.y
        if (movedEnough) dragged = true

        // The finger's speed, smoothed a little: a single event's own speed is
        // noise on a touch panel, and the gain changes with it.
        val gap = (event.eventTime - lastMoveAt).coerceAtLeast(1L)
        lastMoveAt = event.eventTime
        val instant = kotlin.math.hypot(dxView, dyView) / gap
        moveSpeed += (instant - moveSpeed) * SPEED_SMOOTHING

        val gain = PointerSpeed.gain(moveSpeed)
        owedMoveX += dxView * scaleX * gain
        owedMoveY += dyView * scaleY * gain
        val (stepX, leftX) = PointerSpeed.spend(owedMoveX)
        val (stepY, leftY) = PointerSpeed.spend(owedMoveY)
        owedMoveX = leftX
        owedMoveY = leftY

        // Added to where the arrow already is — not used as an absolute position.
        val fromX = if (pointerX >= 0) pointerX else picture.width / 2
        val fromY = if (pointerY >= 0) pointerY else picture.height / 2
        val wantX = (fromX + stepX).coerceIn(0, picture.width - 1)
        val wantY = (fromY + stepY).coerceIn(0, picture.height - 1)
        pointerX = wantX
        pointerY = wantY
        markCursor(wantX, wantY)

        // Held down for a double-tap-and-drag: the button stays pressed and the
        // object follows the pointer.
        sendMove(socket, wantX, wantY)
    }

    /** A tap clicks at the pointer, never at the finger. */
    private fun endOneFinger(event: MotionEvent) {
        val socket = socket ?: return
        val at = arrow()
        val now = System.currentTimeMillis()

        if (held) {
            // The press that a double tap started is let go here. A move that
            // was still owed is dropped rather than left to fire after the
            // release: the button goes up at the newest position below, and a
            // move arriving later would drag an object nothing is holding.
            pointerSettled = true
            removeCallbacks(settleRun)
            at?.let { (x, y) -> socket.up(x, y, "left") }
            held = false
            return
        }
        if (dragged || movedEnough) return
        if (at == null) return

        val isDouble = secondTap && now - lastTapAt <= DOUBLE_TAP_MS
        if (isDouble) {
            // The viewer's click-and-hold: press and keep it pressed, so a drag
            // that follows moves the object rather than the pointer.
            val button = "left"
            socket.down(at.first, at.second, button)
            held = true
            secondTap = false
            return
        }
        // Which button a tap clicks is decided by how many fingers made it, not
        // by how many are left at the end — by then the others have lifted — and
        // a button the reader chose in the bar wins over the count.
        val button = PointerPlan.tapButton(maxFingers)
        socket.down(at.first, at.second, button)
        socket.up(at.first, at.second, button)
    }

    /**
     * Two fingers: pinch to zoom, move together to scroll the machine, or both.
     *
     * RealVNC spells these out — "pinch with two fingers to zoom", and "place
     * two fingers and drag up or down to scroll" — and doing them from one
     * gesture is what makes the picture feel attached to the hand rather than
     * switching between modes.
     */
    private fun moveTwoFingers(event: MotionEvent) {
        val socket = socket ?: return
        val apart = distance(event)
        val ratio = if (pinchStartDistance > 0f) apart / pinchStartDistance else 1f
        val wanted = (pinchStartZoom * ratio).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val dx = focusX(event) - twoFingerX
        val dy = focusY(event) - twoFingerY
        if (abs(wanted - zoom) > 0.002f || abs(dx) > 0.5f || abs(dy) > 0.5f) pinched = true

        // The picture follows the fingers exactly: zoom about the point held,
        // and move by however far the pair moved.
        applyZoom(wanted, pinchFocusX, pinchFocusY, pinchStartPanX + dx, pinchStartPanY + dy)

        // Scrolling the machine is a separate thing from moving the picture, and
        // RealVNC offers it with the same two fingers, both ways: "drag up or
        // down to scroll the current application vertically, or drag left or
        // right to scroll horizontally". The two axes are counted apart and
        // spent apart, so a sideways drag never moves the page and a diagonal
        // one does both; the remainder stays with the touch, so a slow drag
        // scrolls slowly instead of dying under the first threshold.
        if (!pinched || abs(wanted - pinchStartZoom) < 0.01f) {
            val focusNowX = focusX(event)
            val focusNowY = focusY(event)
            owedScrollX += focusNowX - scrollFromX
            owedScrollY += focusNowY - scrollFromY
            scrollFromX = focusNowX
            scrollFromY = focusNowY
            val (px, py) = arrow() ?: (0 to 0)
            val across = PointerPlan.spend(owedScrollX, SCROLL_STEP_PX)
            if (across != 0) {
                owedScrollX -= across * SCROLL_STEP_PX
                socket.wheel(px, py, across, axis = ScrollAxis.HORIZONTAL)
                scrolled = true
            }
            val down = PointerPlan.spend(owedScrollY, SCROLL_STEP_PX)
            if (down != 0) {
                owedScrollY -= down * SCROLL_STEP_PX
                socket.wheel(px, py, down, axis = ScrollAxis.VERTICAL)
                scrolled = true
            }
        }
    }

    /**
     * Moves the arrow out from under a touch that landed on top of it.
     *
     * Only in the pad mode: in touch mode the press belongs where the finger is
     * and moving the pointer would move the click. The arrow is nudged to the
     * nearest visible place — along the line from the finger through itself —
     * and the machine is told, because a cursor that only looks moved is a
     * cursor that clicks where the picture no longer shows it.
     */
    private fun keepArrowVisible(event: MotionEvent) {
        val socket = socket ?: return
        val picture = surface?.bitmap() ?: return
        val at = arrow() ?: return
        if (picture.width <= 0 || picture.height <= 0) return
        val box = pictureRect(picture.width, picture.height)
        if (box.width() <= 0f || box.height() <= 0f) return
        val scaleX = box.width() / picture.width
        val scaleY = box.height() / picture.height
        val arrowX = box.left + at.first * scaleX
        val arrowY = box.top + at.second * scaleY
        val moved = PointerPlan.clearOfFinger(
            finger = event.x to event.y,
            arrow = arrowX to arrowY,
            clear = ARROW_CLEARANCE_PX * resources.displayMetrics.density,
        ) ?: return
        val to = toScreen(moved.first, moved.second) ?: return
        pointerX = to.first
        pointerY = to.second
        markCursor(to.first, to.second)
        sendMove(socket, to.first, to.second)
    }

    /** Keeps the drawn arrow where the local finger just put it. */
    private fun markCursor(x: Int, y: Int) {
        surface?.cursorTo(x to y)
        postInvalidateOnAnimation()
    }

    /**
     * Where the arrow is as far as the finger is concerned.
     *
     * While a finger owns it that is the local copy, because the machine's echo
     * is a frame behind and adding movements to a stale position is what made a
     * drag travel further the faster it went. With no finger down the machine's
     * word is the only one, and it also catches a pointer moved by another
     * viewer.
     */
    private fun arrow(): Pair<Int, Int>? {
        if (pointerX >= 0 && pointerY >= 0) return pointerX to pointerY
        return surface?.cursor
    }

    /**
     * Sets the zoom, keeping the point under the fingers where it is.
     *
     * The picture is drawn centred plus `pan`; growing it by a factor while the
     * focus stays put means moving the centre by the same proportion, which is
     * what makes a pinch feel anchored rather than skating.
     */
    private fun applyZoom(wanted: Float, focusX: Float, focusY: Float, basePanX: Float, basePanY: Float) {
        // Derived from where the pinch began, never from the frame before.
        // Compounding on the running zoom made every event depend on the last
        // one, so the base pan and the zoom disagreed and the picture shifted
        // under the fingers a little more each time — which reads as a stutter.
        val previous = pinchStartZoom
        val zoom = wanted.coerceIn(MIN_ZOOM, MAX_ZOOM)
        this.zoom = zoom
        val growth = if (previous <= 0f) 1f else zoom / previous
        // What is being zoomed about is the pointer, not the middle of the
        // fingers. Moving the desktop so the arrow stays where it is on the
        // picture is what keeps the thing being worked on in view: pinching to
        // read something small would otherwise throw it off the screen. It
        // slides only when the arrow is against an edge, because the picture
        // stops at the desktop's own bounds.
        val centreX = width / 2f
        val centreY = height / 2f
        val anchor = anchorFor(focusX, focusY)
        panX = basePanX + (anchor.first - centreX - basePanX) * (1 - growth)
        panY = basePanY + (anchor.second - centreY - basePanY) * (1 - growth)
        clampPan()
        // On the next frame, not now: touch events arrive faster than the screen
        // refreshes, and drawing per event is the other half of the stutter.
        postInvalidateOnAnimation()
    }

    /**
     * The point a pinch should keep still, in view coordinates.
     *
     * The pointer's place on screen, so zooming holds on to whatever is under it.
     * Falls back to the middle of the fingers when the pointer's place is not
     * known, which is the best a pinch can do then.
     */
    private fun anchorFor(focusX: Float, focusY: Float): Pair<Float, Float> {
        val picture = surface?.bitmap()
        val at = surface?.cursor
        if (picture == null || at == null || picture.width <= 0 || picture.height <= 0) {
            return focusX to focusY
        }
        val box = pictureRect(picture.width, picture.height)
        if (box.width() <= 0f || box.height() <= 0f) return focusX to focusY
        val x = box.left + at.first * (box.width() / picture.width)
        val y = box.top + at.second * (box.height() / picture.height)
        return x to y
    }

    companion object {
        /** Movement in view pixels before a press becomes a drag. */
        private const val TOUCH_SLOP = 12f

        /** Movement before a two-finger drag becomes a scroll notch. */
        private const val SCROLL_STEP_PX = 24f

        /** How much the picture may grow; below 1 it would be a keyhole. */
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 4f

        /**
         * The shortest gap between two pointer moves, in milliseconds.
         *
         * Sixteen is one frame at sixty a second: sending faster than the
         * machine can redraw costs a queue and gains nothing visible.
         */
        private const val MOVE_INTERVAL_MS = 16L

        /**
         * How much of each new speed reading is believed, from 0 to 1.
         *
         * A touch panel's own speed between two events is noisy; a quarter of
         * each reading is enough to follow a hand without following the noise.
         */
        private const val SPEED_SMOOTHING = 0.25f

        /** How soon a second tap counts as a double tap. */
        private const val DOUBLE_TAP_MS = 300L

        /** The arrow's size at 1:1, and the largest it is allowed to grow. */
        private const val CURSOR_SIZE = 14f
        private const val CURSOR_MAX = 30f

        /**
         * How far from a finger the arrow is kept, in dp.
         *
         * A thumb covers roughly this much of the picture, so an arrow nearer
         * than this to the touch point is an arrow nobody can see.
         */
        private const val ARROW_CLEARANCE_PX = 44f
    }
}
