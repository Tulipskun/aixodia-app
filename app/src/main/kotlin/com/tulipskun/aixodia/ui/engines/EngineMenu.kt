package com.tulipskun.aixodia.ui.engines

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.theme.Space
import com.tulipskun.aixodia.ui.theme.aixodia

/** The row names this sheet uses for "which one is working". */
internal const val UPDATE = "update"
internal const val BACKUP = "backup"

/**
 * The sheet behind a card's **More**.
 *
 * A card has room for three verbs beside it; everything else a machine can be
 * asked to do would crowd those three into seven. The sheet is the second
 * shelf: the machine's name at the top, then one row per verb, each with the
 * sentence that says what it will actually do.
 *
 * The rows are three because three things exist to be said. A fourth that
 * nothing implements would be drawn disabled and would teach the reader that
 * the sheet is decoration; adding one here is one row and one call site.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.s4)) {
            // The console's head: what this is about, and what it is.
            Text(
                engine.name,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (engine.online) "engine menu" else "engine menu · unreachable",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Space.s3))

            MenuRow(
                icon = Icons.Default.SystemUpdateAlt,
                title = "Update engine",
                detail = "Install the published build. It swaps itself, same pid.",
                busy = working == UPDATE,
                enabled = working.isEmpty(),
                onClick = onUpdate,
            )
            MenuRow(
                icon = Icons.Default.CloudUpload,
                title = "Back up now",
                detail = "Start a backup of this machine's files.",
                busy = working == BACKUP,
                enabled = working.isEmpty(),
                onClick = onBackup,
            )
            MenuRow(
                icon = Icons.Default.Folder,
                title = "Files",
                detail = "Browse and upload on this machine.",
                busy = false,
                enabled = working.isEmpty(),
                onClick = onFiles,
            )

            if (note.isNotEmpty()) {
                Spacer(Modifier.height(Space.s2))
                Box(
                    Modifier.fillMaxWidth().height(1.dp)
                        .background(MaterialTheme.aixodia.line),
                )
                Spacer(Modifier.height(Space.s2))
                Text(
                    note,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.s4))
        }
    }
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
