package com.tulipskun.aixodia.readings

import com.tulipskun.aixodia.data.remote.Health
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The window a card draws, and the way it survives the app being closed.
 *
 * The graph is the reason this is stored at all, so what is pinned here is that a
 * window comes back the same length and in the same order it was written — and
 * that a file this app did not write, or no longer understands, is an empty card
 * rather than a crash on the front door of the app.
 */
class EngineReadingsTest {

    private val gigabyte = 1024L * 1024 * 1024

    private fun health(cpu: Double, usedGb: Long, totalGb: Long = 8) = Health(
        cpuPercent = cpu,
        cpuCores = 4.0,
        memoryUsedBytes = usedGb * gigabyte,
        memoryTotalBytes = totalGb * gigabyte,
        diskUsedBytes = 11L * gigabyte,
        diskTotalBytes = 40L * gigabyte,
    )

    @Test
    fun `a sample is added to every series and said in gigabytes`() {
        val readings = EngineReadings().with(health(cpu = 33.0, usedGb = 3))
        assertEquals(listOf(33.0), readings.cpu)
        assertEquals(listOf(3.0), readings.ram)
        assertEquals(listOf(11.0), readings.disk)
        assertEquals("8", readings.ramTotalGb)
        assertEquals("40", readings.diskTotalGb)
        assertEquals("4", readings.cpuCores)
    }

    @Test
    fun `the cpu share keeps the whole it is a share of`() {
        val readings = EngineReadings().with(health(cpu = 12.0, usedGb = 1))
            .with(Health(cpuPercent = 13.0, cpuCores = 1.5))
        assertEquals(listOf(12.0, 13.0), readings.cpu)
        assertEquals("1.5", readings.cpuCores)
        // An engine that cannot say how many cores says nothing rather than zero.
        assertEquals("1.5", readings.with(Health(cpuPercent = 14.0)).cpuCores)
        assertEquals(null, EngineReadings().with(Health(cpuPercent = 15.0)).cpuCores)
    }

    @Test
    fun `a metric the engine leaves out is not appended as a zero`() {
        val readings = EngineReadings().with(health(cpu = 40.0, usedGb = 2))
            .with(Health(cpuPercent = 41.0))
        assertEquals(listOf(40.0, 41.0), readings.cpu)
        // Memory and disk said nothing in the second reply, so they keep the
        // readings they had rather than gaining a phantom sample.
        assertEquals(listOf(2.0), readings.ram)
        assertEquals(listOf(11.0), readings.disk)
        // A capacity is a fact, not a sample: it survives a reply without it.
        assertEquals("8", readings.ramTotalGb)
    }

    @Test
    fun `the window stops at its width`() {
        var readings = EngineReadings()
        repeat(READINGS_WINDOW + 5) { index -> readings = readings.with(health(cpu = index.toDouble(), usedGb = 1)) }
        assertEquals(READINGS_WINDOW, readings.cpu.size)
        // Newest last, and the oldest five are what fell off.
        assertEquals(5.0, readings.cpu.first(), 0.001)
        assertEquals((READINGS_WINDOW + 4).toDouble(), readings.cpu.last(), 0.001)
    }

    @Test
    fun `a window comes back out of the store the way it went in`() {
        val byEngine = mapOf(
            "m-8a7d1192" to EngineReadings(
                cpu = listOf(8.0, 33.5),
                ram = listOf(2.1, 4.9),
                disk = listOf(11.2),
                ramTotalGb = "8",
                diskTotalGb = "40",
            ),
            "work-1" to EngineReadings(cpu = listOf(1.0)),
        )
        val decoded = EngineReadings.decodeAll(EngineReadings.encodeAll(byEngine))
        assertEquals(byEngine, decoded)
    }

    @Test
    fun `nothing stored, and nothing readable, are both an empty set`() {
        assertTrue(EngineReadings.decodeAll("").isEmpty())
        assertTrue(EngineReadings.decodeAll("not json").isEmpty())
        assertTrue(EngineReadings.decodeAll("{}").isEmpty())
    }

    @Test
    fun `an unreadable series inside a readable window is skipped, not fatal`() {
        val decoded = EngineReadings.decodeAll(
            """{"m-1":{"cpu":[1,"x",3],"ram":null,"ramTotal":"8"}}""",
        )
        val card = decoded.getValue("m-1")
        assertEquals(listOf(1.0, 3.0), card.cpu)
        assertTrue(card.ram.isEmpty())
        assertEquals("8", card.ramTotalGb)
        assertEquals(null, card.diskTotalGb)
    }
}
