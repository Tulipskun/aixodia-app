package com.tulipskun.aixodia.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The history, as a thumb walks through it.
 *
 * Every assertion here is a pair of presses: where a person got to, and where
 * back has to put them. The defects that motivated these are in the names.
 */
class NavTest {
    @Test
    fun the_app_opens_on_the_engines_list_and_back_leaves_it() {
        val nav = Nav()
        assertEquals(Place.Engines, nav.current)
        assertFalse("the home has nothing behind it", nav.canGoBack)
        assertFalse("back at the home is the app leaving", nav.back())
        assertEquals(Place.Engines, nav.current)
    }

    @Test
    fun settings_opened_from_the_list_returns_to_the_list() {
        // The defect this pins: settings used to return to the chat, a screen the
        // reader had never opened.
        val nav = Nav()
        nav.open(Place.Settings)
        assertEquals(Place.Settings, nav.current)
        assertTrue(nav.back())
        assertEquals(Place.Engines, nav.current)
    }

    @Test
    fun settings_opened_from_the_chat_returns_to_the_chat() {
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Settings)
        assertTrue(nav.back())
        assertEquals(Place.Chat, nav.current)
    }

    @Test
    fun a_pane_opened_from_a_card_returns_to_the_list() {
        val nav = Nav()
        nav.open(Place.Terminal)
        assertTrue(nav.back())
        assertEquals(Place.Engines, nav.current)
    }

    @Test
    fun a_file_manager_opened_from_settings_returns_to_settings() {
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Settings)
        nav.open(Place.Files)
        assertTrue(nav.back())
        assertEquals(Place.Settings, nav.current)
    }

    @Test
    fun opening_the_place_already_showing_is_not_a_step() {
        // A double tap on a card must not need two presses of back to undo.
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Chat)
        assertEquals(listOf(Place.Engines, Place.Chat), nav.places)
        nav.back()
        assertEquals(Place.Engines, nav.current)
    }

    @Test
    fun closing_a_terminal_lands_in_the_chat_it_was_opened_from() {
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Terminal)
        assertTrue(nav.backTo(Place.Chat))
        assertEquals(Place.Chat, nav.current)
        assertEquals(listOf(Place.Engines, Place.Chat), nav.places)
    }

    @Test
    fun a_terminal_opened_from_a_card_has_no_chat_behind_it() {
        // Its caller is told so, and closes onto the list instead of leaving a
        // window with no tabs standing over a screen that has moved on.
        val nav = Nav()
        nav.open(Place.Terminal)
        assertFalse(nav.backTo(Place.Chat))
        assertEquals(Place.Terminal, nav.current)
    }

    @Test
    fun the_engines_command_returns_home_in_one_step() {
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Settings)
        nav.home()
        assertEquals(Place.Engines, nav.current)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun the_devices_are_a_place_of_their_own_and_back_returns_to_the_engines() {
        // Droidime is a section, not a filter: it is opened from the drawer, it is
        // not the home, and back from it is the engines list rather than the app
        // closing.
        val nav = Nav()
        nav.open(Place.Droidime)
        assertEquals(Place.Droidime, nav.current)
        assertEquals(Place.Engines, nav.places.first())
        assertTrue(nav.canGoBack)
        assertTrue(nav.back())
        assertEquals(Place.Engines, nav.current)
    }

    @Test
    fun settings_opened_from_the_devices_returns_to_the_devices() {
        val nav = Nav()
        nav.open(Place.Droidime)
        nav.open(Place.Settings)
        assertTrue(nav.back())
        assertEquals(Place.Droidime, nav.current)
    }

    @Test
    fun a_saved_history_survives_a_rotation() {
        val nav = Nav()
        nav.open(Place.Chat)
        nav.open(Place.Screen)
        // What a rotation writes down and reads back: the two halves of the
        // history, as names.
        val restored = Nav.fromNames(Nav.names(nav))
        assertEquals(nav.places, restored.places)
        assertEquals(Place.Screen, restored.current)
    }

    @Test
    fun a_saved_history_from_a_newer_build_still_restores_what_it_knows() {
        // A place this build has no name for must not empty the history.
        val restored = Nav.fromNames(listOf("Engines", "SomethingGone", "Chat"))
        assertEquals(listOf(Place.Engines, Place.Chat), restored.places)
    }
}
