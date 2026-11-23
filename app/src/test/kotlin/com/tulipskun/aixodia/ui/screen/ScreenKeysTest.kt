package com.tulipskun.aixodia.ui.screen

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What an edit key becomes on the machine.
 *
 * These names are looked up on the machine as keysyms, so a wrong name is a key
 * that silently does nothing — which is exactly what a missing backspace looked
 * like.
 */
class ScreenKeysTest {
    @Test
    fun a_backspace_is_the_machines_back_space() {
        assertEquals("BackSpace", ScreenKeys.forEditKey(Key.Backspace))
    }

    @Test
    fun the_arrows_move_the_machines_caret() {
        assertEquals("Left", ScreenKeys.forEditKey(Key.DirectionLeft))
        assertEquals("Right", ScreenKeys.forEditKey(Key.DirectionRight))
        assertEquals("Up", ScreenKeys.forEditKey(Key.DirectionUp))
        assertEquals("Down", ScreenKeys.forEditKey(Key.DirectionDown))
        assertEquals("Home", ScreenKeys.forEditKey(Key.MoveHome))
        assertEquals("End", ScreenKeys.forEditKey(Key.MoveEnd))
    }

    @Test
    fun a_letter_is_not_an_edit_key() {
        // It travels as text, in whatever language it was typed in.
        assertNull(ScreenKeys.forEditKey(Key.A))
        assertNull(ScreenKeys.forEditKey(Key.Spacebar))
    }
}
