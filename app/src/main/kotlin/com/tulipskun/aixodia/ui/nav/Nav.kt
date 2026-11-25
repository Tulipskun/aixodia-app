package com.tulipskun.aixodia.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue

/**
 * Everything the app can be showing.
 *
 * The list of engines is the home, and every other place is opened from it or
 * from the chat on top of it.
 */
enum class Place {
    /** The home screen: the engines the gateway knows about. */
    Engines,

    /**
     * The phones DroidIME runs on.
     *
     * A section of its own rather than a filter on the engine list, because a
     * device is not a machine of ours: it has no shells, no desktop and no
     * readings to chart, and its card offers two verbs rather than four. The web
     * console separates them at the same place — one menu entry each — and the
     * two screens must not disagree about what a phone is.
     */
    Droidime,

    /** One engine's chat: the pane a card's AI action opens. */
    Chat,

    /** One engine's shells. */
    Terminal,

    /** One engine's desktop. */
    Screen,

    Settings,
    Backup,
    Files,
    Providers,
    ;

    companion object {
        /** The place a saved name means, or nothing when the name is unknown. */
        fun parse(name: String): Place? = entries.firstOrNull { it.name == name }
    }
}

/**
 * The app's history, oldest first, with the last entry showing.
 *
 * One list rather than a flag per screen. A flag can say "settings is up" but
 * not where settings was opened from, and that is the whole of what back needs
 * to know: settings opened from the list returns to the list, settings opened
 * from the chat's drawer returns to the chat. Two booleans cannot say that, and
 * the app had six of them.
 *
 * The home is the first entry and is never popped: when the history holds only
 * the home, [back] answers false and the app is allowed to leave.
 */
class Nav(initial: List<Place> = listOf(Place.Engines)) {
    var places by mutableStateOf(initial.ifEmpty { listOf(Place.Engines) })
        private set

    val current: Place get() = places.last()

    val canGoBack: Boolean get() = places.size > 1

    /** Opens a place on top of the one showing. The place already showing is not a step. */
    fun open(place: Place) {
        if (place == current) return
        places = places + place
    }

    /** Goes back one place; false when this is home and the app should leave. */
    fun back(): Boolean {
        if (!canGoBack) return false
        places = places.dropLast(1)
        return true
    }

    /**
     * Shows the newest occurrence of [place] and drops everything above it.
     *
     * Closing a terminal lands in the chat it was opened from, not one place up:
     * the tabs are gone, so the pane behind the window is the only one that
     * still has something to show. False when the history never held [place] —
     * a terminal opened from a card has no chat behind it, and its caller has to
     * decide where that goes.
     */
    fun backTo(place: Place): Boolean {
        val index = places.indexOfLast { it == place }
        if (index < 0) return false
        places = places.take(index + 1)
        return true
    }

    /** Returns to the home screen in one step, however deep the history is. */
    fun home() {
        places = listOf(places.first())
    }

    companion object {
        /**
         * The history as names, the way it is written into saved state.
         *
         * The names are the two halves of what a rotation has to keep: the place
         * showing, and the places back returns to.
         */
        fun names(nav: Nav): ArrayList<String> = ArrayList(nav.places.map { it.name })

        /** The history a saved list of names means; a name this build has no place for is dropped. */
        fun fromNames(names: List<String>): Nav = Nav(names.mapNotNull { Place.parse(it) })

        val Saver: Saver<Nav, ArrayList<String>> = Saver(
            save = { nav -> names(nav) },
            restore = { names -> fromNames(names) },
        )
    }
}
