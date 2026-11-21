package com.tulipskun.aixodia.ui.files

/**
 * The engine's upload endpoint takes one file per request, so a selection of ten
 * goes up as ten requests. That is why this loop exists as its own thing rather
 * than inline in the screen: what a batch promises is small but it is a promise.
 *
 * Two files out of ten being fine and the eighth being refused has to end with
 * the other nine on disk and the eighth named. A batch that stopped at the first
 * failure would leave a person guessing which of their files made it, and one
 * that failed quietly would tell them nothing at all.
 *
 * `items` is the selection in the order the picker handed it over, paired with
 * the name each file will be written as. `onProgress` is told which file the bar
 * belongs to (`2/10  photo.jpg`, or just the name when there is one file), and
 * then how far that file has got. The result is one line per file that failed,
 * in selection order, ready to be shown as it is.
 */
internal suspend fun <T> uploadBatch(
    items: List<Pair<T, String>>,
    onProgress: (label: String, sent: Long, total: Long) -> Unit,
    upload: suspend (item: T, name: String, onProgress: (sent: Long, total: Long) -> Unit) -> Unit,
): List<String> {
    val failures = mutableListOf<String>()
    items.forEachIndexed { index, item ->
        val name = item.second
        val label = if (items.size == 1) name else "${index + 1}/${items.size}  $name"
        onProgress(label, 0L, -1L)
        // The name is in the message because the message is the only place it
        // can be: the bar has already moved on to the next file by the time
        // anyone reads the list.
        runCatching {
            upload(item.first, name) { sent, total -> onProgress(label, sent, total) }
        }.onFailure { failures += name + ": " + (it.message ?: "upload failed") }
    }
    return failures
}
