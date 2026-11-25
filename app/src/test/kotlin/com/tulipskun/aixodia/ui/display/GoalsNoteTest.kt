package com.tulipskun.aixodia.ui.display

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The goals and plans note is a plain message in the thread. It must keep every
 * item — a finished goal is marked, not removed — and must not repeat the
 * instructions that are meant for the model alone.
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
    fun every_item_is_kept_with_its_status() {
        assertEquals(
            listOf(
                "**Goals & plans**",
                "Goals:",
                "- [g1] open · ship the release",
                "- [g2] done · fix the installer",
                "- [g3] dropped · rewrite the web console",
                "Plans:",
                "- [p1] open · write tests",
            ).joinToString("\n"),
            noteText(reminder),
        )
    }

    @Test
    fun the_instructions_to_the_model_are_not_drawn() {
        val note = noteText(reminder)
        assertEquals(false, note.contains("Keep them in mind"))
        assertEquals(false, note.contains("system-remind"))
    }

    @Test
    fun a_reminder_with_no_items_draws_nothing() {
        assertEquals("", noteText("<system-remind>\nNo goals yet.\n</system-remind>"))
        assertEquals("", noteText(""))
    }
}
