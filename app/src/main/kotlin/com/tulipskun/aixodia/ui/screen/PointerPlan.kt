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
 * The shape of that curve came from measuring a stock RealVNC Viewer 4.9.6
 * against this machine's own server, with drags of known length and duration:
 * the arrow took between 0.38 and 0.60 of the finger's travel. Its ends are
 * deliberate rather than its middle: the top is that 0.6, and the bottom is
 * above the 0.3 share this viewer used to apply flat.
 */
object PointerSpeed {
    /** Share of the finger's travel taken at a crawl, for aiming. */
    const val SLOW = 0.40f

    /** Share taken at a flick, for crossing the screen. */
    const val FAST = 0.85f

    /**
     * The speed, in view pixels per millisecond, at which [FAST] is reached.
     *
     * An ordinary drag is 300 to 900 view pixels a second, which is 0.3 to 0.9
     * per millisecond: the top of the curve has to be inside that band, or a
     * normal flick is treated as a careful movement and crawls.
     */
    const val FAST_AT = 0.5f

    /**
     * The share of a movement to apply at [pxPerMs].
     *
     * Linear between the two ends: below a crawl it is [SLOW], above [FAST_AT]
     * it is [FAST], and between them it rises with the speed, which is the whole
     * of what "acceleration" means here.
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

/**
 * The arithmetic of a pinch.
 *
 * The picture is drawn centred plus a pan, so zooming about a point means moving
 * the pan in the opposite direction by the amount the zoom just gained. Doing
 * that from the state the pinch began in — rather than from the running one — is
 * what keeps it still: a pan derived from the live pan and the live zoom feeds
 * each step back into the next, and the picture drifts, then snaps back when the
 * arrow reaches an edge.
 */
object Pinch {
    /**
     * The pan that keeps the point at [anchor] where it is while the picture is
     * scaled from [drawnAtBase] × [baseZoom] to [drawnAtBase] × [zoom].
     *
     * [anchor] and [centre] are in view pixels, [drawnAtBase] is the picture's
     * drawn size at the fitted zoom, and [basePan] is the pan that was in force
     * when the fingers landed.
     */
    fun pan(
        anchor: Float,
        centre: Float,
        drawnAtBase: Float,
        basePan: Float,
        baseZoom: Float,
        zoom: Float,
    ): Float {
        if (baseZoom <= 0f || drawnAtBase <= 0f) return basePan
        val growth = zoom / baseZoom
        val leftBase = centre - drawnAtBase / 2f + basePan
        // The picture point under the anchor is kept under it: its distance from
        // the left edge grows by exactly the zoom's growth.
        val left = anchor - (anchor - leftBase) * growth
        return left - (centre - drawnAtBase * growth / 2f)
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
}
