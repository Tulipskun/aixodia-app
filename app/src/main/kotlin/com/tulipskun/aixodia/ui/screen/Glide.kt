package com.tulipskun.aixodia.ui.screen

/**
 * How a picture that has to move gets there: quickly at first, then settling.
 *
 * Three things about the pane's own movement have been reported from the phone
 * and only one of them is about distance: it jumped, it jumped back, and it
 * arrived somewhere else. A movement that is spread over a few frames with an
 * ease at the end reads as the picture being pushed rather than teleported, and
 * it is arithmetic — so it is here, where a test can see it, rather than spread
 * through an `onTouchEvent` that no test can reach.
 */
object Glide {
    /** How long a move takes. Long enough to see, short enough not to wait for. */
    const val MS = 220L

    /**
     * The share of a move done at [t], where [t] runs 0 to 1.
     *
     * Ease-out cubic: three quarters of the distance is covered in the first
     * half, which is what makes a small move feel immediate and a long one feel
     * like it has weight. It starts at exactly 0 and ends at exactly 1, so a
     * move that is not finished never leaves the picture short of its target.
     */
    fun eased(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        val left = 1f - clamped
        return 1f - left * left * left
    }

    /** Where a value has got to at [t], between [from] and [to]. */
    fun at(from: Float, to: Float, t: Float): Float = from + (to - from) * eased(t)
}
