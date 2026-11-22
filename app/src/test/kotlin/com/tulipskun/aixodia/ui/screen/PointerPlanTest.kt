package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mouse-control rules, checked against RealVNC's own table.
 *
 * The table is not folklore: it ships inside the APK as `assets/help/usage.html`,
 * with a picture for each row — "Your finger moves the mouse cursor rather than
 * clicking where you touch"; tap one finger to left-click "at the point the
 * mouse cursor is positioned (not your finger)"; two fingers right, three
 * middle; two fingers drag to scroll "vertically or horizontally"; "tap twice,
 * hold the second tap, and drag" to move an object.
 *
 * Which button a tap clicks is checked in [TapButtonTest]; what is left here is
 * the arithmetic that can go wrong without a touch screen: how a two-finger
 * drag becomes notches on two axes, and where the arrow goes when a thumb lands
 * on it.
 */
class PointerPlanTest {

    // --- Two-finger scrolling, on either axis ------------------------------

    @Test
    fun sidewaysMovementSpendsHorizontalNotchesOnly() {
        // 60 view pixels across a 24-pixel step is two notches across, and none
        // down: a sideways drag must not scroll the page.
        assertEquals(2, PointerPlan.spend(60f, 24f))
        assertEquals(0, PointerPlan.spend(0f, 24f))
    }

    @Test
    fun upwardMovementIsNegativeAndSpendsNegativeNotches() {
        assertEquals(-2, PointerPlan.spend(-60f, 24f))
    }

    @Test
    fun aMovementShorterThanAStepSpendsNothing() {
        assertEquals(0, PointerPlan.spend(23f, 24f))
        assertEquals(0, PointerPlan.spend(-23f, 24f))
    }

    @Test
    fun aSlowDragKeepsItsRemainderInsteadOfDying() {
        // Three drags of 10 pixels earn one notch between them, not none: the
        // remainder has to survive between events or a slow drag never scrolls.
        var owed = 0f
        var notches = 0
        repeat(3) {
            owed += 10f
            val spent = PointerPlan.spend(owed, 24f)
            owed -= spent * 24f
            notches += spent
        }
        assertEquals(1, notches)
        assertEquals(6f, owed, 0.01f)
    }

    @Test
    fun aLongDragIsNotLimitedToOneNotchPerEvent() {
        // A flick covers several steps at once; sending one notch would make a
        // long list feel stuck.
        assertEquals(5, PointerPlan.spend(125f, 24f))
    }

    // --- Keeping the arrow visible under a thumb ---------------------------

    @Test
    fun anArrowUnderTheFingerIsMovedOutToTheClearance() {
        val moved = PointerPlan.clearOfFinger(
            finger = 100f to 400f,
            arrow = 104f to 398f,
            clear = 44f,
        )
        requireNotNull(moved)
        val distance = kotlin.math.hypot(moved.first - 100f, moved.second - 400f)
        assertEquals(44f, distance, 0.01f)
    }

    @Test
    fun theArrowMovesAlongTheLineItAlreadyHas() {
        // Straight above the finger stays straight above it: the smallest move
        // that makes it visible, not a jump to some fixed corner.
        val moved = PointerPlan.clearOfFinger(
            finger = 500f to 500f,
            arrow = 500f to 480f,
            clear = 44f,
        )
        requireNotNull(moved)
        assertEquals(500f, moved.first, 0.01f)
        assertEquals(456f, moved.second, 0.01f)
    }

    @Test
    fun anArrowExactlyUnderTheFingerGoesUpAndLeft() {
        // No direction to keep, and the up-left side is where the hand is not.
        val moved = PointerPlan.clearOfFinger(
            finger = 200f to 200f,
            arrow = 200f to 200f,
            clear = 44f,
        )
        requireNotNull(moved)
        assertTrue(moved.first < 200f)
        assertTrue(moved.second < 200f)
    }

    @Test
    fun anArrowAlreadyInSightIsLeftAlone() {
        // Outside the thumb's reach: moving it would be a jump the reader did
        // not ask for.
        assertNull(
            PointerPlan.clearOfFinger(
                finger = 100f to 100f,
                arrow = 300f to 300f,
                clear = 44f,
            ),
        )
    }

    @Test
    fun a_crawl_keeps_a_fifth_of_the_movement_and_a_flick_two_fifths() {
        // The two ends of the curve, from RealVNC's own measured table. A slow
        // hand gets fine aim; a fast one crosses the screen.
        assertEquals(PointerSpeed.SLOW, PointerSpeed.gain(0f), 0.0001f)
        // Just off a standstill it has already begun to rise, which is the point
        // of the ramp: there is no speed at which the arrow ignores the hand.
        assertTrue(PointerSpeed.gain(0.05f) > PointerSpeed.SLOW)
        assertEquals(PointerSpeed.FAST, PointerSpeed.gain(PointerSpeed.FAST_AT), 0.0001f)
        assertEquals(PointerSpeed.FAST, PointerSpeed.gain(9f), 0.0001f)
    }

    @Test
    fun the_gain_rises_with_the_speed_and_never_falls() {
        var previous = PointerSpeed.gain(0f)
        var speed = 0f
        while (speed <= 1.5f) {
            val gain = PointerSpeed.gain(speed)
            assertTrue("gain fell at $speed", gain >= previous - 0.0001f)
            previous = gain
            speed += 0.05f
        }
        // Halfway up the ramp is halfway between the two measured ends.
        assertEquals(
            (PointerSpeed.SLOW + PointerSpeed.FAST) / 2f,
            PointerSpeed.gain(PointerSpeed.FAST_AT / 2f),
            0.0001f,
        )
    }

    @Test
    fun a_fraction_of_a_pixel_is_kept_not_thrown_away() {
        // At the slow end one event of a careful drag is well under a pixel.
        // Dropping it would make a slow drag move nothing at all.
        val (step, left) = PointerSpeed.spend(0.4f)
        assertEquals(0, step)
        assertEquals(0.4f, left, 0.0001f)
        val (next, rest) = PointerSpeed.spend(left + 0.4f)
        assertEquals(0, next)
        assertEquals(0.8f, rest, 0.0001f)
        val (third, _) = PointerSpeed.spend(rest + 0.4f)
        assertEquals(1, third)
    }

    @Test
    fun a_movement_to_the_left_is_spent_to_the_left() {
        val (step, left) = PointerSpeed.spend(-2.5f)
        assertEquals(-2, step)
        assertEquals(-0.5f, left, 0.0001f)
    }
}
