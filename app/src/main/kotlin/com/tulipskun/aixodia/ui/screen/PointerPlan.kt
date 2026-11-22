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