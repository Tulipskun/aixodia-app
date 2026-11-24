package com.tulipskun.aixodia.ui.engines

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

/**
 * What **More** shows: three floating buttons in the middle of the screen.
 *
 * No list, no captions — an icon each, at the centre, over the page blurred
 * behind them. The page is the context: the card that was pressed is still
 * under the blur in the place it was, so the buttons are read as belonging to
 * that machine rather than to a new screen.
 *
 * The blur belongs to the caller (it has to be applied to the content), so this
 * draws only the scrim, the buttons and the one line of feedback.
 */
@Composable
internal fun EngineActions(
    engine: EngineEntry,
    working: String,
    note: String,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val idle = working.isEmpty()
    Box(Modifier.fillMaxSize()) {
        // Tapping the blur dismisses; it must not also reach the card under it.
        Box(
            Modifier
                .fillMaxSize()
                // The page under this is nearly black, so a black scrim only makes
                // the blur read as "the screen went dark". A thin white veil
                // instead lifts the blurred shapes to where the eye can see them
                // as shapes without any of the text being readable.
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s3)) {
                FloatingAction(
                    icon = Icons.Default.SystemUpdateAlt,
                    label = "Update",
                    description = "Update ${engine.name}",
                    busy = working == UPDATE,
                    enabled = idle,
                    onClick = onUpdate,
                )
                FloatingAction(
                    icon = Icons.Default.CloudUpload,
                    label = "Back up",
                    description = "Back up ${engine.name}",
                    busy = working == BACKUP,
                    enabled = idle,
                    onClick = onBackup,
                )
                FloatingAction(
                    icon = Icons.Default.Folder,
                    label = "Files",
                    description = "Files on ${engine.name}",
                    busy = false,
                    enabled = idle,
                    onClick = onFiles,
                )
            }
            // The machine's name, or the answer it just gave.
            Spacer(Modifier.height(Space.s4))
            Text(
                if (note.isNotEmpty()) note else engine.name,
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
    onClick: () -> Unit,
) {
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
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
