package com.tulipskun.aixodia.ui.files

import com.tulipskun.aixodia.data.remote.RemoteFile
import org.junit.Assert.assertEquals
import org.junit.Test

private fun file(
    name: String,
    type: String = "file",
    size: Long = 0L,
): RemoteFile = RemoteFile(
    name = name,
    path = "/root/$name",
    type = type,
    size = size,
    modified = 0L,
    permissions = "0644",
    uid = -1L,
    gid = -1L,
)

class FileListTest {

    private val entries = listOf(
        file("zeta.txt", size = 30L),
        file("b", type = "directory"),
        file("Alpha.md", size = 5L),
        file("link", type = "symlink", size = 1L),
        file("a.log", size = 5L),
        file("A", type = "directory"),
    )

    @Test
    fun `name sort ignores case and keeps folders above files`() {
        val names = sortedEntries(entries, FileSort(FileSortKey.Name)).map { it.name }
        assertEquals(listOf("A", "b", "a.log", "Alpha.md", "link", "zeta.txt"), names)
    }

    @Test
    fun `descending name sort reverses the column but not the folder group`() {
        val names = sortedEntries(entries, FileSort(FileSortKey.Name, descending = true)).map { it.name }
        assertEquals(listOf("b", "A", "zeta.txt", "link", "Alpha.md", "a.log"), names)
    }

    @Test
    fun `size sort breaks ties by name and leaves folders on top`() {
        val names = sortedEntries(entries, FileSort(FileSortKey.Size)).map { it.name }
        assertEquals(listOf("A", "b", "link", "a.log", "Alpha.md", "zeta.txt"), names)
        val desc = sortedEntries(entries, FileSort(FileSortKey.Size, descending = true)).map { it.name }
        assertEquals(listOf("b", "A", "zeta.txt", "Alpha.md", "a.log", "link"), desc)
    }

    @Test
    fun `type sort groups by kind then by extension`() {
        val names = sortedEntries(entries, FileSort(FileSortKey.Type)).map { it.name }
        assertEquals(listOf("A", "b", "a.log", "Alpha.md", "zeta.txt", "link"), names)
    }

    @Test
    fun `copy paths follows the displayed order and only names selected rows`() {
        val ordered = sortedEntries(entries, FileSort(FileSortKey.Name))
        val selected = setOf("/root/zeta.txt", "/root/a.log")
        assertEquals("/root/a.log\n/root/zeta.txt", pathsToCopy(ordered, selected))
        assertEquals("", pathsToCopy(ordered, emptySet()))
        assertEquals("/root/zeta.txt", pathsToCopy(ordered, setOf("/root/zeta.txt")))
    }

    @Test
    fun `a stored sort survives the round trip and bad values fall back`() {
        assertEquals(FileSort(FileSortKey.Size, true), FileSort.parse("size:desc"))
        assertEquals(FileSort(), FileSort.parse("nonsense"))
        assertEquals("name:asc", FileSort().stored())
        assertEquals("type:desc", FileSort(FileSortKey.Type, true).stored())
    }
}
