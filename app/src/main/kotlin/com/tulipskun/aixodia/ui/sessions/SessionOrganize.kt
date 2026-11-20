@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.tulipskun.aixodia.ui.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tulipskun.aixodia.R
import com.tulipskun.aixodia.ui.theme.aixodia
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.tulipskun.aixodia.data.model.ChatSession
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * ChatGPT-style session grouping by recency.
 * Buckets: วันนี้ · เมื่อวาน · 7 วันล่าสุด · เก่ากว่า
 */
data class SessionGroup(
    val label: String,
    /** The same label as a resource, so a translation does not have to parse it. */
    val labelRes: Int,
    val sessions: List<ChatSession>,
)

fun groupSessionsByDate(sessions: List<ChatSession>, nowMs: Long = System.currentTimeMillis()): List<SessionGroup> {
    if (sessions.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    cal.timeInMillis = nowMs
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val startOfToday = cal.timeInMillis
    val startOfYesterday = startOfToday - TimeUnit.DAYS.toMillis(1)
    val startOfWeek = startOfToday - TimeUnit.DAYS.toMillis(7)

    val today = mutableListOf<ChatSession>()
    val yesterday = mutableListOf<ChatSession>()
    val week = mutableListOf<ChatSession>()
    val older = mutableListOf<ChatSession>()

    // Newest first within each bucket, with the same id tiebreaker Room uses.
    // Chats created in the same second otherwise shuffle between redraws.
    sessions.sortedWith(compareByDescending<ChatSession> { it.lastAt }.thenByDescending { it.id })
        .forEach { s ->
        val t = if (s.lastAt > 0L) s.lastAt else 0L
        when {
            t >= startOfToday -> today += s
            t >= startOfYesterday -> yesterday += s
            t >= startOfWeek -> week += s
            else -> older += s
        }
    }
    return buildList {
        if (today.isNotEmpty()) add(SessionGroup("Today", R.string.group_today, today))
        if (yesterday.isNotEmpty()) add(SessionGroup("Yesterday", R.string.group_yesterday, yesterday))
        if (week.isNotEmpty()) add(SessionGroup("Last 7 days", R.string.group_week, week))
        if (older.isNotEmpty()) add(SessionGroup("Older", R.string.group_older, older))
    }
}

fun sessionTitle(sessions: List<ChatSession>, id: String): String =
    sessions.firstOrNull { it.id == id }?.title?.takeIf { it.isNotBlank() } ?: id.ifBlank { "New chat" }

/**
 * Drawer content: search + new chat + date-grouped session list.
 * Typical AI chatbot sidebar pattern (ChatGPT / Claude mobile).
 */
@Composable
fun SessionDrawerContent(
    sessions: List<ChatSession>,
    activeId: String,
    onNewChat: () -> Unit,
    onOpen: (String) -> Unit,
    onLongPress: (ChatSession) -> Unit,
    footer: @Composable () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        if (query.isBlank()) sessions
        else sessions.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.lastSnippet.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }
    }
    val groups = remember(filtered) { groupSessionsByDate(filtered) }

    // The panel is the page, not a card laid on it: the console opens its menu
    // from the page's own surface, with one hairline between the two.
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
        drawerTonalElevation = 0.dp,
        drawerShape = RoundedCornerShape(0.dp),
    ) {
        // The list scrolls and takes the room it needs; the footer stays pinned
        // to the bottom of the sheet. Sharing one scroll left the footer
        // floating mid-sheet with dead space under it.
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            // Lazy, not a Column that composes every row: a few hundred chats
            // were all built and measured on every open, and the search filter
            // walked the whole set on the main thread.
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // A list says what it is before it lists anything: one label, the
                // count beside it, and no heading competing with the bar above.
                item(key = "header") {
                    Text(
                        stringResource(R.string.drawer_chats).uppercase() + "  \u00b7  " +
                            pluralStringResource(R.plurals.drawer_sessions, sessions.size, sessions.size),
                        modifier = Modifier.padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.9.sp,
                    )
                }
                item(key = "new") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.aixodia.line, MaterialTheme.shapes.small)
                            .clickable(onClick = onNewChat)
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(R.string.new_chat),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.drawer_search)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
                item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 12.dp)) }
                if (sessions.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.drawer_empty_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.drawer_empty_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (groups.isEmpty()) {
                    item(key = "nomatch") {
                        Text(
                            stringResource(R.string.drawer_no_match, query),
                            Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "g-" + group.label) {
                            Text(
                                stringResource(group.labelRes).uppercase(),
                                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.9.sp,
                            )
                        }
                        items(group.sessions, key = { it.id }) { s ->
                            SessionRow(
                                s = s,
                                active = s.id == activeId,
                                onOpen = { onOpen(s.id) },
                                onLongPress = { onLongPress(s) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))
            footer()
        }
    }
}

@Composable
fun SessionRow(
    s: ChatSession,
    active: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
) {
    // A row carries its state on the row itself, the way the console's session
    // rows do: the current one is a step lighter with a hairline around it, and
    // nothing is tinted blue to say "selected".
    val bg = if (active) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(
                1.dp,
                if (active) MaterialTheme.aixodia.line else Color.Transparent,
                MaterialTheme.shapes.small,
            )
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
            // The active chat was a tint, an icon and a weight — three visuals and
            // no semantics, so a screen reader heard two identical rows.
            .semantics { selected = active }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                s.title.ifBlank { s.id },
                maxLines = 1,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                s.lastSnippet.ifBlank { stringResource(R.string.no_messages_yet) },
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.unread > 0) {
            AssistChip(onClick = onOpen, label = { Text("${s.unread}") })
        }
    }
}

@Composable
fun ChatActionsSheet(
    session: ChatSession,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(session.title.ifBlank { session.id }, maxLines = 2) },
        text = {
            Column {
                Text(
                    if (session.lastSnippet.isBlank()) stringResource(R.string.no_messages_yet)
                    else session.lastSnippet.take(80),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onRename) { Text(stringResource(R.string.rename)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }
        },
    )
}

@Composable
fun RenameChatDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_chat_title)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text(stringResource(R.string.chat_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(field) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun DeleteChatDialog(
    title: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_chat_title)) },
        text = {
            Text(stringResource(R.string.delete_chat_body, title))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
