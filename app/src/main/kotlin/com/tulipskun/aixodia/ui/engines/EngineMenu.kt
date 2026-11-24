package com.tulipskun.aixodia.ui.engines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.theme.Space
import com.tulipskun.aixodia.ui.theme.aixodia

/** The row names this menu uses for "which one is working". */
internal const val UPDATE = "update"
internal const val BACKUP = "backup"

/**
 * The menu behind a card's **More**: a panel in the middle of the screen.
 *
 * A card has room for three verbs beside it; everything else a machine can be
 * asked to do would crowd those three into seven. This is the second shelf —
 * the machine's name, then one row per verb with the sentence that says what it
 * will actually do.
 *
 * It sits in the centre rather than rising from the bottom because it is a
 * question about *this card*, asked while looking at the list: a sheet covers
 * the card it belongs to, and the reader loses the machine they are deciding
 * about. The panel keeps the list visible behind it and the card in place.
 *
 * The rows are three because three things exist to be said. A fourth that
 * nothing implements would be drawn disabled and would teach the reader that
 * the menu is decoration; adding one is one row and one call site.
 */
@Composable
internal fun EngineMenu(
    engine: EngineEntry,
    working: String,
    note: String,
    onUpdate: () -> Unit,
    onBackup: () -> Unit,
    onFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // The default width is a percentage of the window taken by the dialog
        // system; this panel decides its own, so a long description line is not
        // wrapped by a number nobody here chose.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                .border(1.dp, MaterialTheme.aixodia.line, shape)
                .padding(horizontal = Space.s4, vertical = Space.s3),
        ) {
            // The console's head: what this is about, and what it is.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    engine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Space.s3))
                Text(
                    if (engine.online) "engine menu" else "unreachable",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.s2))
            Hairline()

            MenuRow(
                icon = Icons.Default.SystemUpdateAlt,
                title = "Update engine",
                detail = "Install the published build. It swaps itself, same pid.",
                busy = working == UPDATE,
                enabled = working.isEmpty(),
                onClick = onUpdate,
            )
            Hairline()
            MenuRow(
                icon = Icons.Default.CloudUpload,
                title = "Back up now",
                detail = "Start a backup of this machine's files.",
                busy = working == BACKUP,
                enabled = working.isEmpty(),
                onClick = onBackup,
            )
            Hairline()
            MenuRow(
                icon = Icons.Default.Folder,
                title = "Files",
                detail = "Browse and upload on this machine.",
                busy = false,
                enabled = working.isEmpty(),
                onClick = onFiles,
            )

            // The result of the last row, under them: the answer belongs to
            // the row that was pressed, so it is read in the same place. Only
            // there when there is something to say — an empty two-line box
            // under the last row reads as a fourth row that failed to load.
            if (note.isNotEmpty()) {
                Spacer(Modifier.height(Space.s2))
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The hairline the console draws where two darks meet. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.aixodia.line))
}

/** One verb: an icon, a name, and the sentence that keeps it honest. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    detail: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface
              else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.s3),
    ) {
        Icon(
            icon, contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary
                   else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
