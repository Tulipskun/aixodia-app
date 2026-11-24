package com.tulipskun.aixodia.ui.engines

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.theme.Space
import com.tulipskun.aixodia.ui.theme.aixodia

/** The names this overlay uses for "which one is working". */
internal const val UPDATE = "update"
internal const val BACKUP = "backup"
internal const val RENAME = "rename"
internal const val DELETE = "delete"

/**
 * The veil over the page: a very pale grey, not white.
 *
 * White over a near-black page is a different colour; grey is the same page one
 * step lighter, which is what a blur needs to be seen against.
 */
private val Veil = Color(0xFFC6CBD4)
private const val VEIL_ALPHA = 0.13f

/** How long the tiles take to arrive, and to leave. */
private const val ENTER_MS = 200
private const val EXIT_MS = 140

/**
 * What **More** shows: the verbs a card has no room for, as tiles in the middle
 * of the screen, over the page.
 *
 * No list: a verb written on a tile each, at the centre, over the page blurred
 * behind them. The page is the context — the card that was pressed is still
 * under the blur where it was — so the tiles are read as belonging to that
 * machine rather than to a new screen.
 *
 * The tiles wrap three to a row. Four of them in one row is 388dp, wider than a
 * 320dp phone, and a menu that runs off the screen edge is a verb nobody can
 * reach. A second row is centred under the first, so the group still reads as
 * one thing rather than as two lists.
 *
 * The blur belongs to the caller (it has to be applied to the content), so this
 * draws the veil, the tiles and the one line of feedback. Everything it draws
 * arrives and leaves on `open`: a menu that appears in one frame reads as a
 * change of screen, and this is not a screen but a question about the card.
 *
 * `open` is a transition state rather than a flag because the overlay is only
 * composed once it has something to show — a flag would already say "open" on
 * the first frame and there would be nothing to animate from. The caller owns
 * the state so it can close the menu and still hold it composed until the exit
 * has played.
 */
@Composable
internal fun EngineActions(
    engine: EngineEntry,
    open: MutableTransitionState<Boolean>,
    working: String,
    note: String,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onFiles: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val idle = working.isEmpty()
    // What the machine is called, in one place: the tiles' labels and the line
    // under them must name the same thing, and an engine with a name of its own
    // must never be captioned with the id it no longer shows.
    val title = engine.displayName
    // The veil is not faded in, and that is a measurement rather than a taste:
    // a full-screen alpha that changes has to be redrawn whole every frame, and
    // each of those frames measured 250–600 ms on this phone (a swipe of the
    // list measures the same, so the page is what is expensive, not the blur).
    // A veil that is simply on, with only the tiles moving, leaves nothing on
    // the screen to redraw but three small tiles.
    Box(Modifier.fillMaxSize()) {
        // Tapping the veil dismisses; it must not also reach the card under it.
        Box(
            Modifier
                .fillMaxSize()
                // The page under this is nearly black, so a black scrim only makes
                // the blur read as "the screen went dark". A very pale grey lifts
                // the blurred shapes to where the eye can see them as shapes while
                // no text becomes readable again — and it is grey, not white, so
                // the page stays the same colour it was, one step lighter.
                .background(
                    if (open.targetState) Veil.copy(alpha = VEIL_ALPHA)
                    else Color.Transparent,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        AnimatedVisibility(
            visibleState = open,
            enter = fadeIn(tween(ENTER_MS)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(ENTER_MS)),
            exit = fadeOut(tween(EXIT_MS)) +
                scaleOut(targetScale = 0.96f, animationSpec = tween(EXIT_MS)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            // The tiles, three to a row. Each row is its own Row so the widths
            // stay the tiles' own instead of being stretched by a weight.
            TILES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                    row.forEach { tile ->
                        FloatingAction(
                            icon = tile.icon,
                            label = tile.label,
                            description = "${tile.label} $title",
                            busy = tile.working?.let { working == it } ?: false,
                            enabled = idle,
                            destructive = tile.destructive,
                            onClick = tile.act(onUpdate, onBackup, onFiles, onRename, onDelete),
                        )
                    }
                }
                if (row !== TILES.chunked(3).last()) Spacer(Modifier.height(Space.s3))
            }
            // The machine's name, or the answer it just gave.
            Spacer(Modifier.height(Space.s4))
            Text(
                if (note.isNotEmpty()) note else title,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                color = if (note.isNotEmpty()) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.s5),
            )
                }
        }
    }
}

/** What one tile is, and which of the two names of "working" it answers to. */
private class Tile(
    val icon: ImageVector,
    val label: String,
    /** The [working] value this tile shows as a spinner, or null if it has none. */
    val working: String? = null,
    val destructive: Boolean = false,
    /** Which verb of the menu this tile is. */
    val verb: String,
)

private val TILES = listOf(
    Tile(Icons.Default.SystemUpdateAlt, "Update", working = UPDATE, verb = UPDATE),
    Tile(Icons.Default.CloudUpload, "Back up", working = BACKUP, verb = BACKUP),
    Tile(Icons.Default.Folder, "Files", verb = "files"),
    Tile(Icons.Default.DriveFileRenameOutline, "Rename", working = RENAME, verb = RENAME),
    Tile(Icons.Default.DeleteOutline, "Delete", working = DELETE, destructive = true, verb = DELETE),
)

/** The press one tile makes, chosen by the verb written on it. */
private fun Tile.act(
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onFiles: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
): () -> Unit = when (verb) {
    UPDATE -> onUpdate
    BACKUP -> onBackup
    "files" -> onFiles
    RENAME -> onRename
    DELETE -> onDelete
    else -> error("a tile with no verb")
}

/**
 * One tile: an icon over a word, in a rounded square.
 *
 * The shape and the word are what make three buttons a menu rather than three
 * unnamed symbols: the verb is written on the thing that does it, the way a
 * status bar's tiles do it. The corner radius is the cards' radius, one step
 * tighter, so a tile reads as belonging to the same page.
 */
@Composable
private fun FloatingAction(
    icon: ImageVector,
    label: String,
    description: String,
    busy: Boolean,
    enabled: Boolean,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val ink = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .size(width = 88.dp, height = 72.dp)
            // The page behind this is blurred, so a tile has to be lit: the
            // highest surface step and the louder hairline, or dark squares on a
            // dark page read as nothing to press.
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, TileShape)
            .border(1.dp, MaterialTheme.aixodia.lineStrong, TileShape)
            .clickable(enabled = enabled, onClickLabel = description, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(Space.s1))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A tile's corners: the cards' radius, one step tighter. */
private val TileShape = RoundedCornerShape(12.dp)
