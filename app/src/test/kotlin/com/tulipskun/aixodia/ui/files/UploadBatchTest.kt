package com.tulipskun.aixodia.ui.files

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadBatchTest {

    @Test
    fun `a refused file does not stop the rest of the selection`() = runTest {
        val attempted = mutableListOf<String>()
        val failures = uploadBatch(
            items = listOf("a.jpg" to "a.jpg", "b.jpg" to "b.jpg", "c.jpg" to "c.jpg"),
            onProgress = { _, _, _ -> },
            upload = { _, name, _ ->
                attempted += name
                if (name == "b.jpg") throw IllegalStateException("name already exists")
            },
        )

        assertEquals(listOf("a.jpg", "b.jpg", "c.jpg"), attempted)
        assertEquals(listOf("b.jpg: name already exists"), failures)
    }

    @Test
    fun `the bar says which file of how many it belongs to`() = runTest {
        val labels = mutableListOf<String>()
        uploadBatch(
            items = listOf("one" to "a.txt", "two" to "b.txt"),
            onProgress = { label, sent, _ -> labels += "$label@$sent" },
            upload = { _, _, report ->
                report(512L, 1024L)
            },
        )

        assertEquals(listOf("1/2  a.txt@0", "1/2  a.txt@512", "2/2  b.txt@0", "2/2  b.txt@512"), labels)
    }

    @Test
    fun `a single file is named without a count`() = runTest {
        val labels = mutableListOf<String>()
        uploadBatch(
            items = listOf("only" to "notes.md"),
            onProgress = { label, _, _ -> labels += label },
            upload = { _, _, _ -> },
        )

        assertEquals(listOf("notes.md"), labels)
    }

    @Test
    fun `a failure with no message is still named`() = runTest {
        val failures = uploadBatch(
            items = listOf("x" to "empty.bin"),
            onProgress = { _, _, _ -> },
            upload = { _, _, _ -> throw IllegalStateException() },
        )

        assertEquals(1, failures.size)
        assertTrue(failures.single().startsWith("empty.bin: "))
    }

    @Test
    fun `an empty selection reports nothing`() = runTest {
        val labels = mutableListOf<String>()
        val failures = uploadBatch<String>(
            items = emptyList(),
            onProgress = { label, _, _ -> labels += label },
            upload = { _, _, _ -> throw AssertionError("must not upload anything") },
        )

        assertTrue(labels.isEmpty())
        assertTrue(failures.isEmpty())
    }
}
