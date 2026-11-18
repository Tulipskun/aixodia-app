package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.ConnConfig
import com.tulipskun.aixodia.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The switcher's two decisions: what to show, and what to write when the user
 * picks. A list that cannot be refreshed must not empty the screen, and a pick
 * must never be written as half a switch.
 */
class EngineDirectoryTest {

    private val body = """
        {"engines":[
          {"id":"m-8a7d1192","name":"Primary Engine",
           "url":"https://engine.aixodia.dpdns.org/m-8a7d1192","online":true,"version":"v1.177"},
          {"id":"work-1","name":"Work","url":"https://engine.aixodia.dpdns.org/work-1"}
        ]}
    """.trimIndent()

    private suspend fun settings(json: String = body, activeId: String = "", daemon: String = "https://engine.aixodia.dpdns.org"): SettingsStore {
        val settings: SettingsStore = mock()
        whenever(settings.engineSnapshot()).thenReturn(SettingsStore.EngineSnapshot(json, activeId, 1_700_000_000))
        whenever(settings.current()).thenReturn(
            ConnConfig("", daemon, "", "", "", "s", sessionToken = "A"),
        )
        return settings
    }

    @Test
    fun `what to show is what was stored, with the pick honoured`() = runBlocking {
        val directory = EngineDirectory(settings(activeId = "work-1"))
        val snapshot = directory.snapshot()
        assertEquals(listOf("m-8a7d1192", "work-1"), snapshot.engines.map { it.id })
        assertEquals("work-1", snapshot.active?.id)
        assertEquals(1_700_000_000L, snapshot.fetchedAt)
    }

    @Test
    fun `a pick writes the engine and the address it names together`() = runBlocking {
        val settings = settings()
        val picked = EngineDirectory(settings).select("work-1")
        assertEquals("work-1", picked?.id)
        verify(settings).selectEngine("work-1", "https://engine.aixodia.dpdns.org/work-1")
    }

    @Test
    fun `a name the list does not have changes nothing`() = runBlocking {
        val settings = settings()
        assertNull(EngineDirectory(settings).select("gone"))
        verify(settings, never()).selectEngine(any(), any())
    }

    @Test
    fun `a registry that cannot be reached leaves the stored list standing`() = runBlocking {
        // Port 1 on the loopback refuses immediately: this is the offline case,
        // not a slow one.
        val settings = settings(daemon = "https://127.0.0.1:1")
        val snapshot = EngineDirectory(settings).refresh()
        assertEquals(2, snapshot.engines.size)
        verify(settings, never()).saveEngines(any())
    }
}
