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

    /** How far apart two places may be and still be the same place, in pixels. */
    const val PLACE_SLACK = 2

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

    /**
     * Where a cursor bitmap is drawn, given the pointer's place on screen.
     *
     * The hot spot — the pixel of the image that *is* the pointer — goes on the
     * pointer, and the rest of the image hangs around it. The image is scaled
     * with the picture so it looks the size the desktop drew it, but never below
     * its own pixels: a fitted picture would otherwise shrink an I-beam to a
     * smudge.
     */
    fun bitmapRect(
        pointerX: Float,
        pointerY: Float,
        width: Int,
        height: Int,
        hotX: Int,
        hotY: Int,
        scale: Float,
    ): FloatBox {
        val drawn = max(1f, scale)
        val left = pointerX - hotX * drawn
        val top = pointerY - hotY * drawn
        return FloatBox(left, top, width * drawn, height * drawn)
    }

    /**
     * True when a shape's place is the place the arrow is drawn at.
     *
     * A picture pixel or two of slack, because the two numbers are read at
     * slightly different moments and a pointer that is standing still can be a
     * rounding apart. Anything more than that is not the same place.
     */
    fun samePlace(one: Pair<Int, Int>?, other: Pair<Int, Int>?): Boolean {
        if (one == null || other == null) return false
        return kotlin.math.abs(one.first - other.first) <= PLACE_SLACK &&
            kotlin.math.abs(one.second - other.second) <= PLACE_SLACK
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
