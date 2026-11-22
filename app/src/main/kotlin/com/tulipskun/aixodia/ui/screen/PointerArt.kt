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
