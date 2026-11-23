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
             "disk_percent":50.0,"io_read_bps":12.5,"io_write_bps":64.0,"uptime_seconds":45600.5,
             "cpu_cores":4.0}
            """.trimIndent(),
        )
        assertEquals(37.4, health?.cpuPercent!!, 0.001)
        assertEquals(4.0, health?.cpuCores!!, 0.001)
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
    fun `a share of nothing is no share at all`() {
        // An engine that cannot read its cgroup quota says nothing about cores;
        // a zero would be a claim that it may use none.
        assertNull(Health.parse("""{"ok":true,"cpu_percent":12.0}""")?.cpuCores)
        assertNull(Health.parse("""{"ok":true,"cpu_cores":0}""")?.cpuCores)
        assertNull(Health.parse("""{"ok":true,"cpu_cores":null}""")?.cpuCores)
    }

    @Test
    fun `a percent is a share of the cores the engine may use`() {
        val health = Health.parse("""{"ok":true,"cpu_percent":12.0,"cpu_cores":1.5}""")
        assertEquals(1.5, health?.cpuCores!!, 0.001)
    }

    @Test
    fun `a percentage is clamped to what a bar can draw`() {
        val health = Health.parse("""{"ok":true,"cpu_percent":137.2,"memory_percent":-4}""")
        assertEquals(100.0, health?.cpuPercent!!, 0.001)
        assertEquals(0.0, health?.memoryPercent!!, 0.001)
    }
}
