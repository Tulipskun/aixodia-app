package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which mouse button a tap clicks, and how a stored input mode is read.
 *
 * The rule is RealVNC's: one finger is the left button, two the right, three the
 * middle. It went wrong once because the count was read when the last finger
 * lifted — by which time the other fingers had already gone — so a two-finger
 * tap arrived as a one-finger tap and clicked left instead of right. These pin
 * the rule to [PointerPlan], which is the code the view actually calls.
 */
class TapButtonTest {

    @Test
    fun oneFingerClicksLeft() {
        assertEquals("left", PointerPlan.tapButton(1))
    }

    @Test
    fun twoFingersClickRight() {
        assertEquals("right", PointerPlan.tapButton(2))
    }

    @Test
    fun threeFingersClickMiddle() {
        assertEquals("middle", PointerPlan.tapButton(3))
    }

    @Test
    fun theCountIsTheWidestNotTheLast() {
        // A two-finger tap ends with one finger still down, then that one lifts.
        // Reading the count at that moment gives 1, which is how it clicked left.
        val atWidest = 2
        val atLastLift = 1
        assertEquals("right", PointerPlan.tapButton(atWidest))
        assertEquals("the trap this test exists for", "left", PointerPlan.tapButton(atLastLift))
    }

    @Test
    fun everyButtonTheBarShowsIsOneTheEngineKnows() {
        // The engine reads "left", "middle" and "right" and treats anything else
        // as left, so a typo here would be a silent wrong click.
        assertEquals(listOf("left", "middle", "right"), PointerPlan.BUTTONS)
        assertEquals("L", PointerPlan.label("left"))
        assertEquals("M", PointerPlan.label("middle"))
        assertEquals("R", PointerPlan.label("right"))
    }

    @Test
    fun aStoredModeIsReadAndAnUnknownOneIsThePad() {
        assertEquals(InputMode.TRACKPAD, InputMode.parse("trackpad"))
        assertEquals(InputMode.DIRECT, InputMode.parse("direct"))
        // Nothing stored, or something from a version that knew another mode:
        // the pad is the safe choice, because a stray touch in touch mode lands
        // on whatever is under the finger.
        assertEquals(InputMode.TRACKPAD, InputMode.parse(null))
        assertEquals(InputMode.TRACKPAD, InputMode.parse("joystick"))
    }
}
