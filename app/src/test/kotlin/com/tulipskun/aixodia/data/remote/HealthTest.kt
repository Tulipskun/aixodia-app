package com.tulipskun.aixodia.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading a card draws comes straight out of `/healthz`, so what the phone
 * accepts from that body is what the graph shows. These are the shapes it has to
 * survive: a full reply, one whose host could not read `/proc`, a dashboard's
 * page, and numbers outside the range a bar can draw.
 */
class HealthTest {

    @Test
    fun `a full reply is read field by field`() {
        val health = Health.parse(
            """
            {"ok":true,"instance":"m-8a7d1192","version":"v1.177",
             "cpu_percent":37.4,"memory_used_bytes":5368709120,"memory_total_bytes":8589934592,
             "memory_percent":62.5,"disk_used_bytes":107374182400,"disk_total_bytes":214748364800,
             "disk_percent":50.0,"io_read_bps":12.5,"io_write_bps":64.0,"uptime_seconds":45600.5}
            """.trimIndent(),
        )
        assertEquals(37.4, health?.cpuPercent!!, 0.001)
        assertEquals(62.5, health?.memoryPercent!!, 0.001)
        assertEquals(5_368_709_120L, health?.memoryUsedBytes)
        assertEquals(8_589_934_592L, health?.memoryTotalBytes)
        assertEquals(50.0, health?.diskPercent!!, 0.001)
        assertEquals(45_600.5, health?.uptimeSeconds!!, 0.001)
        assertEquals(false, health?.isEmpty)
    }

    @Test
    fun `a host that cannot read its own metrics leaves them out`() {
        val health = Health.parse("""{"ok":true,"instance":"m-8a7d1192","cpu_percent":null}""")
        assertNull(health?.cpuPercent)
        assertTrue(health!!.isEmpty)
    }

    @Test
    fun `a dashboard page at that address is not an engine`() {
        assertNull(Health.parse("""{"engines":[]}"""))
        assertNull(Health.parse("<html><body>ok</body></html>"))
        assertNull(Health.parse(""))
    }

    @Test
    fun `a percentage is clamped to what a bar can draw`() {
        val health = Health.parse("""{"ok":true,"cpu_percent":137.2,"memory_percent":-4}""")
        assertEquals(100.0, health?.cpuPercent!!, 0.001)
        assertEquals(0.0, health?.memoryPercent!!, 0.001)
    }
}
