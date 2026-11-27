package com.tulipskun.aixodia.ui.files

import com.tulipskun.aixodia.data.remote.RemoteFile

/** The columns a file manager can order a directory by. */
internal enum class FileSortKey(val value: String, val label: String) {
    Name("name", "Name"),
    Type("type", "Type"),
    Size("size", "Size"),
    ;

    companion object {
        fun from(value: String?): FileSortKey = entries.firstOrNull { it.value == value } ?: Name
    }
}

/** One stored choice, as `"name:asc"` or `"size:desc"`. */
internal data class FileSort(
    val key: FileSortKey = FileSortKey.Name,
    val descending: Boolean = false,
) {
    fun stored(): String = "${key.value}:${if (descending) "desc" else "asc"}"

    companion object {
        fun parse(raw: String?): FileSort {
            val parts = raw?.split(':') ?: return FileSort()
            return FileSort(
                key = FileSortKey.from(parts.getOrNull(0)),
                descending = parts.getOrNull(1) == "desc",
            )
        }
    }
}

internal fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase() else ""
}

/**
 * The listing in the order the chosen column asks for.
 *
 * Folders stay above files whichever way the column runs: a file manager keeps a
 * folder reachable after a size sort, and putting it last would make the first
 * row of a descending list an empty directory. Within each group the name is a
 * tiebreaker, so a redraw never shuffles two files of the same size.
 */
internal fun sortedEntries(entries: List<RemoteFile>, sort: FileSort): List<RemoteFile> {
    val byColumn = when (sort.key) {
        FileSortKey.Name -> compareBy<RemoteFile> { it.name.lowercase() }.thenBy { it.name }
        FileSortKey.Type -> compareBy<RemoteFile> { it.type.lowercase() }
            .thenBy { extensionOf(it.name) }
            .thenBy { it.name.lowercase() }
        FileSortKey.Size -> compareBy<RemoteFile> { it.size }.thenBy { it.name.lowercase() }
    }
    val foldersFirst = compareBy<RemoteFile> { if (it.isDirectory) 0 else 1 }
    return entries.sortedWith(foldersFirst.then(if (sort.descending) byColumn.reversed() else byColumn))
}

/** Selected paths in displayed order, one per line, ready for the clipboard. */
internal fun pathsToCopy(ordered: List<RemoteFile>, selected: Set<String>): String =
    ordered.filter { it.path in selected }.joinToString("\n") { it.path }

/** What the preview screen can show without downloading the whole file. */
internal enum class PreviewKind { Text, Image, Other }

private val TEXT_EXTENSIONS = setOf(
    "", "txt", "md", "json", "csv", "log", "sh", "kt", "kts", "rs", "py", "js", "jsx",
    "ts", "tsx", "html", "htm", "css", "xml", "yml", "yaml", "ini", "conf", "toml",
    "sql", "java", "go", "rb", "php", "diff", "patch", "env",
)
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")

internal fun previewKind(name: String): PreviewKind = when (extensionOf(name)) {
    in IMAGE_EXTENSIONS -> PreviewKind.Image
    in TEXT_EXTENSIONS -> PreviewKind.Text
    else -> PreviewKind.Other
}
