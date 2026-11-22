package com.tulipskun.aixodia.ui.screen

/**
 * The numbers and colours the viewer's chrome is built from.
 *
 * They are not invented: they are read out of RealVNC Viewer 4.9.5's own
 * resources, so the rail on this phone has the same weight as the rail on a
 * RealVNC client — a 54dp floating button, a 260dp pill of 44dp items, black
 * with a 2.5dp grey edge and a rounded end. Keeping them here rather than
 * scattered through the composables means one place changes if they are ever
 * revised, and a test can hold them still.
 */
object SessionLayout {
    /** One toolbar item: the 44dp square an icon sits in. */
    const val ITEM_DP = 44f

    /** The collapsed button, and the height of the expanded pill. */
    const val RAIL_DP = 54f

    /** The expanded pill's width: `dimen/toolbar_layout_width`. */
    const val PILL_DP = 260f

    /** `dimen/fullscreen_toolbar_menu_item_size`. */
    const val STROKE_DP = 2.5f

    /** Distance from the screen's edges, so the button never touches the glass. */
    const val MARGIN_DP = 12f

    /** `color/fullscreen_toolbar_bg`. */
    const val PILL_COLOUR = 0xFF000000L

    /** `color/branded_grey`, the pill's outline. */
    const val EDGE_COLOUR = 0xFF93A0B5L

    /** `color/fullscreen_toolbar_blue`, the item currently open. */
    const val ACTIVE_COLOUR = 0xFF0036D8L

    /** `color/vnc_red`, the item that ends the session. */
    const val LEAVE_COLOUR = 0xFFFF3333L

    /**
     * Where the floating button may sit, given the screen and where a finger
     * has just dragged it.
     *
     * Clamped rather than clipped: a button dragged half past the edge is a
     * button that cannot be pressed again, and on a phone the edge is exactly
     * where a thumb rests. The margin is kept on every side for the same
     * reason — the rail must not sit under the system's own gestures.
     *
     * Coordinates are pixels from the top left of the screen.
     */
    fun clamp(
        x: Float,
        y: Float,
        rail: Float,
        width: Float,
        height: Float,
        margin: Float,
    ): Pair<Float, Float> {
        val maxX = (width - rail - margin).coerceAtLeast(margin)
        val maxY = (height - rail - margin).coerceAtLeast(margin)
        return x.coerceIn(margin, maxX) to y.coerceIn(margin, maxY)
    }

    /**
     * Where the rail starts: the bottom-right corner, the reach of a right
     * thumb, with the margin kept.
     */
    fun home(rail: Float, width: Float, height: Float, margin: Float): Pair<Float, Float> =
        clamp(width - rail - margin, height - rail - margin, rail, width, height, margin)

    /**
     * Where the rail has to sit for its panes to be on the screen.
     *
     * The panes open upward from the rail as one column right-aligned to it, and
     * the capsule is 260dp wide. A rail dragged to the top shows half a pane; one
     * dragged to the left shows half a capsule — both were seen on the phone.
     * The panes are the reason the rail exists, so rather than clip them the rail
     * moves to where they fit, and returns to where the reader put it when they
     * close.
     */
    fun openAt(
        x: Float,
        y: Float,
        rail: Float,
        pill: Float,
        width: Float,
        height: Float,
        margin: Float,
    ): Pair<Float, Float> {
        // The column is right-aligned to the rail, so it is the rail's right
        // edge that has to clear the capsule.
        val fitLeft = margin + pill - rail
        val maxLeft = (width - rail - margin).coerceAtLeast(margin)
        // Enough room for the capsule, unless the screen is too narrow for one.
        val left = x.coerceIn(margin, maxLeft).coerceAtLeast(fitLeft.coerceAtMost(maxLeft))
        val down = if (y < height / 2f) height - rail - margin else y
        return left to down
    }
}
