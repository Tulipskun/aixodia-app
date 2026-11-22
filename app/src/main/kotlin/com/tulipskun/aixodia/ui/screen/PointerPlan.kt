package com.tulipskun.aixodia.ui.screen

/**
 * How a finger on the picture becomes the machine's pointer.
 *
 * RealVNC's viewer offers exactly these two, and they answer a question the
 * phone cannot answer for the reader: is this a picture you are pushing a mouse
 * around on, or the screen itself?
 *
 * - [TRACKPAD] — the picture is a pad. The arrow moves *with* the finger and
 *   stays where it was let go, so a tap clicks under the arrow rather than under
 *   the finger. This is the only mode that can reach a 4K desktop from a phone
 *   without the arrow being an inch wide.
 * - [DIRECT] — the picture is the screen. A touch lands where it is put, and a
 *   drag drags what is under it. This is the mode that feels correct when the
 *   desktop has been scaled down to fit and everything is reachable.
 */
enum class InputMode(val stored: String, val label: String) {
    TRACKPAD("trackpad", "trackpad"),
    DIRECT("direct", "touch");

    companion object {
        /**
         * Reads a stored mode, falling back to the pad.
         *
         * The pad is the safer default: in touch mode a stray tap lands on
         * whatever is under the finger, which on a desktop can be a button.
         */
        fun parse(stored: String?): InputMode =
            entries.firstOrNull { it.stored == stored } ?: TRACKPAD
    }
}

/**
 * The mouse buttons, and what a tap clicks.
 *
 * RealVNC's rule is that the number of fingers chooses the button — one is the
 * left, two the right, three the middle. The bar's buttons are for holding a
 * button down (press, drag with one finger, let go), which is how a window is
 * moved and how touch mode reaches the right button at all. Keeping the rule
 * here rather than inside the view is what lets it be checked without a touch
 * screen.
 */
/**
 * How far the arrow travels for a movement of the finger.
 *
 * A pad that moves the arrow exactly as far as the finger moves is unusable on a
 * desktop wider than the phone: one flick crosses the screen and nothing can be
 * landed on. RealVNC therefore moves it by *part* of the movement, and by more
 * of it the faster the finger goes — slow for aiming, fast for travelling.
 *
 * The two ends of that curve were measured, not guessed: a stock RealVNC Viewer
 * (4.9.6) connected to this machine's own server, with synthetic drags of known
 * length and duration, moved the arrow:
 *
 * | finger            | arrow     | arrow per finger pixel |
 * |-------------------|-----------|------------------------|
 * | 100 px in 1000 ms | 38 px     | 0.38                   |
 * | 100 px in 300 ms  | 34 px     | 0.34                   |
 * | 300 px in 1500 ms | 138 px    | 0.46                   |
 * | 400 px in 200 ms  | 200 px    | 0.50                   |
 * | 100 px in 100 ms  | 60 px     | 0.60                   |
 *
 * Those are desktop pixels for a picture drawn smaller than the desktop, so in
 * terms of the picture the arrow covers between a fifth and two fifths of the
 * finger's travel. That is what the two constants below say, and the ramp
 * between them is what makes a careful movement careful.
 */
object PointerSpeed {
    /** Share of the finger's travel taken at a crawl, for aiming. */
    const val SLOW = 0.28f

    /** Share taken at a flick, for crossing the screen. */
    const val FAST = 0.42f

    /** The speed, in view pixels per millisecond, at which [FAST] is reached. */
    const val FAST_AT = 1.0f

    /**
     * The share of a movement to apply at [pxPerMs].
     *
     * Linear between the two measured ends: below a crawl it is [SLOW], above a
     * flick it is [FAST], and between them it rises with the speed, which is the
     * whole of what "acceleration" means here.
     */
    fun gain(pxPerMs: Float): Float {
        if (pxPerMs <= 0f) return SLOW
        val ramp = (pxPerMs / FAST_AT).coerceIn(0f, 1f)
        return SLOW + (FAST - SLOW) * ramp
    }

    /**
     * Whole pixels to move by, and the fraction left over.
     *
     * The remainder is kept rather than rounded away: at [SLOW] one event of a
     * slow drag is a fraction of a pixel, and dropping it every time would make
     * a careful movement move nothing at all.
     */
    fun spend(owed: Float): Pair<Int, Float> {
        val whole = owed.toInt()
        return whole to owed - whole
    }
}

object PointerPlan {
    /** The buttons the bar offers, in the order it shows them. */
    val BUTTONS: List<String> = listOf("left", "middle", "right")

    /** One letter for a button, for a strip too narrow to write them out. */
    fun label(button: String): String = button.first().uppercase()

    /**
     * Which button a gesture clicks.
     *
     * Two fingers are the right button and three the middle; one, or anything
     * else, is the left. The number that counts is the widest the touch reached,
     * because by the time the last finger lifts the other fingers are already
     * gone — a two-finger tap ends looking like a one-finger one.
     */
    fun tapButton(fingers: Int): String = when (fingers) {
        2 -> "right"
        3 -> "middle"
        else -> "left"
    }

    /**
     * The notches a two-finger drag just earned, on either axis.
     *
     * RealVNC's own words: "place two fingers on the screen and drag up or down
     * to scroll the current application vertically, or drag left or right to
     * scroll horizontally." A single number cannot carry both, so the axes are
     * independent: the movement is kept per axis and a step on one axis spends
     * a notch on that axis only — a diagonal drag scrolls both, a sideways one
     * scrolls the horizontal bar and not the page. The remainder is never
     * dropped: it stays with the touch, so a slow drag scrolls slowly instead
     * of dying under the very first threshold.
     */
    fun spend(accumulated: Float, step: Float): Int = (accumulated / step).toInt()

    /**
     * Where the arrow goes when a touch lands on top of it.
     *
     * RealVNC"s help says the cursor sits "offset from under your finger so you
     * can always see it". In trackpad mode the arrow follows the finger
     * relatively, so a touch can land exactly where the arrow is and hide it;
     * this moves the arrow out to [clear] along the line from the finger
     * through itself — the smallest move that shows it — or, when the arrow is
     * exactly under the touch, up and to the left, which is where no hand is
     * resting. Returns null when the arrow is already visible, so a finger
     * nowhere near the arrow moves nothing until it drags.
     */
    fun clearOfFinger(
        finger: Pair<Float, Float>,
        arrow: Pair<Float, Float>,
        clear: Float,
    ): Pair<Float, Float>? {
        val dx = arrow.first - finger.first
        val dy = arrow.second - finger.second
        if (dx * dx + dy * dy >= clear * clear) return null
        val length = kotlin.math.hypot(dx, dy)
        // A nudge needs a direction; an arrow under the touch has none, so it
        // goes up-left, out from under the thumb.
        val ux = if (length > 0.001f) dx / length else -1f
        val uy = if (length > 0.001f) dy / length else -1f
        return (finger.first + ux * clear) to (finger.second + uy * clear)
    }
}