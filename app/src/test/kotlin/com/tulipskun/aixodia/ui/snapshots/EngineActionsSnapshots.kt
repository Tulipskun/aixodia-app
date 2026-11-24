package com.tulipskun.aixodia.ui.snapshots

import androidx.compose.animation.core.MutableTransitionState
import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.engines.EngineActions
import com.tulipskun.aixodia.ui.engines.DELETE
import com.tulipskun.aixodia.ui.engines.UPDATE
import com.tulipskun.aixodia.ui.theme.AIxodiaTheme
import org.junit.Rule
import org.junit.Test

/**
 * What a card's **More** puts on the screen: labelled tiles in the middle,
 * with the machine's name under them and nothing else.
 *
 * The blur of the page behind belongs to the screen, not to this overlay, so
 * these snapshots pin the part that can be pinned here: the tiles, their
 * spacing, and the one line that says which machine they act on.
 */
class EngineActionsSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    private val engine = EngineEntry(
        id = "m-8a7d1192",
        name = "m-8a7d1192",
        url = "https://engine.aixodia.dpdns.org/m-8a7d1192",
        online = true,
    )

    @Test
    fun engineActions() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineActions(
                engine = engine,
                open = MutableTransitionState(true),
                working = "",
                note = "",
                onUpdate = {},
                onBackup = {},
                onFiles = {},
                onRename = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    /** A row that is working: its button is the spinner, the others stay still. */
    @Test
    fun engineActionsUpdateWorking() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineActions(
                engine = engine,
                open = MutableTransitionState(true),
                working = UPDATE,
                note = "",
                onUpdate = {},
                onBackup = {},
                onFiles = {},
                onRename = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    /** The answer sits under the buttons, in the machine's own monospace. */
    @Test
    fun engineActionsWithAnswer() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineActions(
                engine = engine,
                open = MutableTransitionState(true),
                working = "",
                note = "Update queued — it swaps when no chat is running",
                onUpdate = {},
                onBackup = {},
                onFiles = {},
                onRename = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    /** An engine with a name of its own: the tiles act on the name, not the id. */
    @Test
    fun engineActionsNamed() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineActions(
                engine = engine.copy(alias = "Work bench"),
                open = MutableTransitionState(true),
                working = "",
                note = "",
                onUpdate = {},
                onBackup = {},
                onFiles = {},
                onRename = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    /** Deleting is the last tile, and the only one drawn in the error colour. */
    @Test
    fun engineActionsDeleteWorking() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineActions(
                engine = engine,
                open = MutableTransitionState(true),
                working = DELETE,
                note = "Deleted m-8a7d1192",
                onUpdate = {},
                onBackup = {},
                onFiles = {},
                onRename = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }
}
