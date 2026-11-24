package com.tulipskun.aixodia.ui.screen

import android.graphics.Path
import kotlin.math.max
import kotlin.math.min

/**
 * The arrow the pane draws for the pointer.
 *
 * The machine's own cursor never reaches us: a picture only arrives when pixels
 * change, so the echo of the pointer lags the drag that steered it. This view
 * therefore draws the arrow itself, and its shape matters as much as its place —
 * the first shape had a tail that wandered off its own left edge and a black rim
 * two and a half pixels wide around a fourteen-pixel sprite, which at that size
 * reads as a blob rather than as a pointer. Reported from the phone as "look at
 * the shape of the pointer, why is it like that".
 *
 * So the shape is the ordinary desktop arrow, tip on the pointer, tail inside its
 * own box, and the rim in proportion to the sprite. It lives here, as numbers,
 * so a test can hold it to that.
 */
object PointerArt {

    /** How tall the arrow is drawn when the picture is shown at its own size. */
    const val SIZE = 16f

    /** A zoomed picture may grow the arrow, but not into a badge. */
    const val MAX_SIZE = 34f

    /** The black rim, in view pixels, at [SIZE]. */
    const val RIM = 1f

    /**
     * The arrow as a share of its own height, tip first at (0, 0).
     *
     * Down the left edge from the tip, back to the notch, out to the tail, and
     * up the right side to the point that makes it look like an arrow rather
     * than a flag.
     */
    val OUTLINE: List<Pair<Float, Float>> = listOf(
        0.00f to 0.00f,
        0.00f to 0.86f,
        0.22f to 0.64f,
        0.39f to 1.00f,
        0.57f to 0.90f,
        0.39f to 0.55f,
        0.70f to 0.55f,
    )

    /** How tall to draw it: never smaller than [SIZE] on a shrunken picture. */
    fun size(scaleX: Float, scaleY: Float): Float =
        (SIZE * max(1f, min(scaleX, scaleY))).coerceAtMost(MAX_SIZE)

    /** The rim width that goes with a sprite of [size] view pixels. */
    fun rim(size: Float): Float = RIM * (size / SIZE)

    /** How thick the text cursor's strokes are, at [size]. */
    fun textStroke(size: Float): Float = max(1.6f, size * 0.14f)

    /** How wide its serifs are, at [size]. */
    fun textSerif(size: Float): Float = size * 0.5f

    /**
     * The text cursor's three strokes, centred on ([x], [y]).
     *
     * A bar with a serif at each end, the way desktop pointers have always drawn
     * it. Numbers rather than a path, so a test can hold the shape the way
     * [OUTLINE] holds the arrow's.
     */
    fun textBars(x: Float, y: Float, size: Float): List<FloatBox> {
        val half = size / 2f
        val stroke = textStroke(size)
        val serif = textSerif(size)
        return listOf(
            FloatBox(x - stroke / 2f, y - half, stroke, size),
            FloatBox(x - serif / 2f, y - half, serif, stroke),
            FloatBox(x - serif / 2f, y + half - stroke, serif, stroke),
        )
    }

    /**
     * The text cursor, drawn at ([x], [y]).
     *
     * Drawn rather than echoed for the same reason as the arrow: the machine's
     * echo only arrives with a picture, so it lags the finger. What the machine
     * is asked instead is *what is under the pointer*, and that decides which of
     * these two shapes is drawn.
     */
    fun pathText(x: Float, y: Float, size: Float): Path = Path().apply {
        for (bar in textBars(x, y, size)) {
            addRect(bar.left, bar.top, bar.left + bar.width, bar.top + bar.height, Path.Direction.CW)
        }
    }

    /** The arrow, with its tip on ([x], [y]). */
    fun path(x: Float, y: Float, size: Float): Path = Path().apply {
        val first = OUTLINE.first()
        moveTo(x + first.first * size, y + first.second * size)
        for ((fx, fy) in OUTLINE.drop(1)) {
            lineTo(x + fx * size, y + fy * size)
        }
        close()
    }
}
