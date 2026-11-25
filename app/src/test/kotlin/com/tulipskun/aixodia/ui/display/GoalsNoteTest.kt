package com.tulipskun.aixodia.ui.display

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The goals and plans note is a plain message in the thread, and it is the
 * reminder exactly as the engine sent it: nothing is filtered, shortened or
 * summarised on the way to the reader.
 */
class GoalsNoteTest {

    private val reminder = """
        <system-remind>
        This chat's goals and plans, with the status of each. Keep them in mind; call goal_* or plan_* when one changes. Nothing is removed from this list: completed and dropped items keep their place.

        Goals:
        - [g1] open · ship the release
        - [g2] done · fix the installer
        - [g3] dropped · rewrite the web console

        Plans:
        - [p1] open · write tests
        </system-remind>
    """.trimIndent()

    @Test
    fun the_message_is_the_reminder_whole() {
        assertEquals(reminder, noteText(reminder))
    }

    @Test
    fun every_item_is_kept_with_its_status() {
        val note = noteText(reminder)
        assertEquals(true, note.contains("- [g1] open · ship the release"))
        assertEquals(true, note.contains("- [g2] done · fix the installer"))
        assertEquals(true, note.contains("- [g3] dropped · rewrite the web console"))
        assertEquals(true, note.contains("- [p1] open · write tests"))
    }

    @Test
    fun an_empty_reminder_draws_nothing() {
        assertEquals("", noteText("   \n "))
    }
}
