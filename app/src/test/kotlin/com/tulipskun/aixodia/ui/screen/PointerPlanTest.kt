package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun a_crawl_moves_a_third_of_the_movement_and_a_flick_three_quarters() {
        // The two ends of the curve. A slow hand gets fine aim; a fast one
        // crosses the screen.
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
    fun an_ordinary_drag_reaches_the_top_of_the_curve() {
        // 400 view pixels in half a second is a normal flick, not a careful
        // movement: 0.8 per millisecond, which is past FAST_AT. If the top of
        // the curve sat outside the speeds a hand actually reaches, every drag
        // would crawl — which is exactly how it felt before this was measured.
        val gain = PointerSpeed.gain(400f / 500f)
        assertEquals(PointerSpeed.FAST, gain, 0.0001f)
        // Even a deliberately slow drag of 100 px a second is a third of the way
        // up the ramp, not stuck at the bottom.
        assertTrue(PointerSpeed.gain(100f / 1000f) > PointerSpeed.SLOW + 0.05f)
    }

    // --- Pinching: the picture must not drift or spring back ---------------

    @Test
    fun a_pinch_keeps_the_point_under_the_fingers_where_it_is() {
        // A 1280-wide picture fitted into 640 view pixels: drawn 640 wide, and
        // the pan that was in force when the fingers landed is zero.
        val centre = 320f
        val drawnAtBase = 640f
        val anchor = 200f
        val basePan = 0f
        // The picture point under the anchor, before.
        val before = (anchor - (centre - drawnAtBase / 2f + basePan)) / 1f
        // Zoom to twice the size in one go.
        val pan = Pinch.pan(anchor, centre, drawnAtBase, basePan, 1f, 2f)
        val after = (anchor - (centre - drawnAtBase * 2f / 2f + pan)) / 2f
        assertEquals(before, after, 0.001f)
    }

    @Test
    fun a_pinch_at_the_same_zoom_moves_nothing() {
        assertEquals(0.3f, Pinch.pan(200f, 320f, 640f, 0.3f, 1f, 1f), 0.0001f)
        assertEquals(-7f, Pinch.pan(500f, 320f, 2000f, -7f, 3.2f, 3.2f), 0.0001f)
    }

    @Test
    fun every_step_of_a_pinch_holds_the_same_point_still() {
        // Step the zoom the way touch events do, always from the state the
        // fingers landed in, which is the whole point of the rewrite: the pan is
        // a function of (zoom, anchor), never of the event before it. If any
        // step used the running pan it would drift, and the picture would snap
        // when the arrow reached an edge.
        val centre = 320f
        val drawnAtBase = 640f
        for (anchor in listOf(40f, 260f, 320f, 480f, 600f)) {
            val before = (anchor - (centre - drawnAtBase / 2f)) / 1f
            var zoom = 1f
            while (zoom <= 3f) {
                val pan = Pinch.pan(anchor, centre, drawnAtBase, 0f, 1f, zoom)
                val after = (anchor - (centre - drawnAtBase * zoom / 2f + pan)) / zoom
                assertEquals("anchor $anchor at zoom $zoom", before, after, 0.001f)
                zoom += 0.05f
            }
        }
        // And the same answer whichever event asks: no path dependence.
        assertEquals(
            Pinch.pan(260f, centre, drawnAtBase, 0f, 1f, 2f),
            Pinch.pan(260f, centre, drawnAtBase, 0f, 1f, 2f),
            0f,
        )
    }

    @Test
    fun a_movement_to_the_left_is_spent_to_the_left() {
        val (step, left) = PointerSpeed.spend(-2.5f)
        assertEquals(-2, step)
        assertEquals(-0.5f, left, 0.0001f)
    }

    @Test
    fun the_same_drag_reads_the_same_speed_however_its_samples_arrive() {
        // A hundred pixels in two hundred milliseconds, once reported in five
        // samples and once in fifty: the hand did the same thing, so the gain
        // must be the same. This is what a sample gap could not give.
        fun speedAfter(steps: Int): Float {
            val window = PointerSpeed.SpeedWindow()
            var speed = 0f
            for (i in 0..steps) {
                val k = i.toFloat() / steps
                speed = window.measure((200 * k).toLong(), x = 100f * k, y = 0f)
            }
            return speed
        }
        assertEquals(speedAfter(5), speedAfter(50), 0.02f)
    }

    @Test
    fun a_burst_within_the_window_cannot_fake_a_flick() {
        // Nine samples crammed into twelve milliseconds travel eighty pixels and
        // would read as seven pixels a millisecond, which no hand does. The
        // window has not elapsed, so the speed stands where it was.
        val window = PointerSpeed.SpeedWindow()
        window.measure(0, x = 0f, y = 0f)
        val fresh = window.measure(60, x = 10f, y = 0f)
        assertEquals(10f / 60f, fresh, 0.001f)
        val burst = window.measure(72, x = 90f, y = 0f)
        assertEquals(fresh, burst, 0.0001f)
    }

    @Test
    fun speed_is_capped_and_starts_from_a_standstill() {
        val window = PointerSpeed.SpeedWindow()
        assertEquals(0f, window.measure(0, x = 0f, y = 0f), 0f)
        assertEquals(
            PointerSpeed.CEILING,
            window.measure(60, x = 900f, y = 0f),
            0.0001f,
        )
        window.reset()
        assertEquals(0f, window.measure(400, x = 900f, y = 0f), 0f)
    }

    @Test
    fun a_single_pointer_is_not_a_pinch() {
        // The crash this pins: one finger of a two-finger gesture lifts, and the
        // next batched move carries one pointer. Reading index one of it threw
        // "pointer index out of range" and took the app down from inside input
        // dispatch.
        assertTrue(PointerPlan.carriesTwo(2))
        assertTrue(PointerPlan.carriesTwo(3))
        assertFalse(PointerPlan.carriesTwo(1))
        assertFalse(PointerPlan.carriesTwo(0))
    }

    @Test
    fun an_ordinary_drag_keeps_up_with_the_finger() {
        // Reported from the phone twice as "the mouse is very slow". A drag has
        // to move the pointer at least as far as the thumb travelled; a share
        // below one is exactly the lag the reader kept hitting, and ordinary
        // drags sit at the bottom of the curve where that share was taken.
        var previous = -1f
        for (speed in listOf(0f, 0.05f, 0.1f, 0.3f, 0.6f, 1f, 3f, 9f)) {
            val gain = PointerSpeed.gain(speed)
            assertTrue("gain of $gain at $speed", gain >= 1f)
            assertTrue("gain fell at $speed", gain >= previous - 0.0001f)
            previous = gain
        }
    }
}
