package com.tulipskun.aixodia.ui.screen

import androidx.compose.ui.input.key.Key

/**
 * The keys a phone keyboard sends as an edit rather than as a character.
 *
 * A soft keyboard never sends "backspace" as text: it deletes what it thinks it
 * is editing. When the field in front of it is empty there is nothing to delete
 * there, and the press has to reach the machine instead — otherwise the machine
 * can never be edited at all. The same is true of the arrow keys, which move a
 * caret the field does not have.
 *
 * The names are the machine's, so a lookup that fails on the machine is the
 * machine's problem and not a guess made here.
 */
object ScreenKeys {
    fun forEditKey(key: Key): String? = when (key) {
        Key.Backspace -> BackSpace
        Key.Delete -> Delete
        Key.DirectionLeft -> Left
        Key.DirectionRight -> Right
        Key.DirectionUp -> Up
        Key.DirectionDown -> Down
        Key.MoveHome -> Home
        Key.MoveEnd -> End
        // An empty box has no line to break, so its Enter is the machine's.
        Key.Enter, Key.NumPadEnter -> Return
        else -> null
    }

    const val BackSpace = "BackSpace"
    const val Delete = "Delete"
    const val Left = "Left"
    const val Right = "Right"
    const val Up = "Up"
    const val Down = "Down"
    const val Home = "Home"
    const val End = "End"
    const val Return = "Return"
}
