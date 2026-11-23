package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of a move, without a screen to move on.
 *
 * A picture that has to slide a few hundred pixels to keep the field being
 * typed into in sight is either eased or it teleports. The easing is arithmetic,
 * so it is pinned here: it starts where the picture is, ends where it was told
 * to, and never goes backwards.
 */
class GlideTest {
    @Test
    fun a_move_starts_where_the_picture_is() {
        assertEquals(120f, Glide.at(120f, -300f, 0f), 0.001f)
    }

    @Test
    fun a_move_ends_exactly_where_it_was_told_to() {
        assertEquals(-300f, Glide.at(120f, -300f, 1f), 0.001f)
        // Even if a frame arrives late: the end is the end.
        assertEquals(-300f, Glide.at(120f, -300f, 4f), 0.001f)
    }

    @Test
    fun most_of_the_distance_is_covered_early() {
        // Ease-out: half the time is more than half the journey, which is what
        // makes the move feel attached to the finger rather than lagging behind.
        assertTrue(Glide.eased(0.5f) > 0.6f)
        assertTrue(Glide.eased(0.5f) < 1f)
    }

    @Test
    fun a_move_never_goes_backwards() {
        var last = -1f
        for (step in 0..20) {
            val value = Glide.at(0f, 100f, step / 20f)
            assertTrue("at step $step", value >= last)
            last = value
        }
    }

    @Test
    fun a_move_to_the_place_it_is_already_at_says_nothing() {
        assertEquals(0f, Glide.eased(0f), 0f)
        assertEquals(1f, Glide.eased(1f), 0f)
    }
}
