package com.tulipskun.aixodia.readings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The console's own arithmetic, checked against the numbers the web prints.
 *
 * These are ports, not inventions: a card that says `23.1/123 GB` on the web and
 * `23.0/123 GB` on the phone is a bug a reader cannot explain, so the format and
 * the chart's geometry are pinned here.
 */
class MetricShapeTest {

    @Test
    fun `a share is said in whole numbers with its percent sign`() {
        val (value, unit) = MetricShape.reading(listOf(20.0, 37.4), digits = 0, unit = "%", total = null)
        assertEquals("37", value)
        assertEquals("%", unit)
    }

    @Test
    fun `a metric with a capacity is said as used over whole`() {
        val (value, unit) = MetricShape.reading(
            listOf(22.0, 23.14),
            digits = 1,
            unit = "GB",
            total = "123",
        )
        assertEquals("23.1/123", value)
        assertEquals("GB", unit)
    }

    @Test
    fun `nothing sampled is an em dash and no unit, not a zero`() {
        assertEquals("—" to "", MetricShape.reading(emptyList(), digits = 1, unit = "GB", total = "123"))
    }

    @Test
    fun `a total the engine did not report leaves the share as a plain number`() {
        // `23.1/ GB` would read as a machine with an unknown capacity rather than as
        // a reading that simply has none.
        assertEquals("23.1" to "GB", MetricShape.reading(listOf(23.14), 1, "GB", total = ""))
    }

    @Test
    fun `bytes become gigabytes, and a capacity is said whole`() {
        val eightGb = 8L * 1024 * 1024 * 1024
        assertEquals(8.0, MetricShape.toGb(eightGb)!!, 0.0001)
        assertEquals("8", MetricShape.wholeGb(eightGb))
        assertEquals(null, MetricShape.wholeGb(null))
    }

    @Test
    fun `a falling window runs from the top of the box to the floor`() {
        // y counts down the canvas, so the high reading is the top of the box.
        val points = MetricShape.points(listOf(100.0, 0.0), ceiling = 100.0)
        assertEquals(2, points.size)
        assertEquals(MetricShape.SIDE, points.first().first, 0.001)
        assertEquals(MetricShape.TOP, points.first().second, 0.001)
        assertEquals(100.0 - MetricShape.SIDE, points.last().first, 0.001)
        assertEquals(MetricShape.FLOOR, points.last().second, 0.001)
    }

    @Test
    fun `a steady window is drawn flat in the middle, not as a cliff`() {
        // The span floor is what stops four tenths of a percent from filling the cell
        // like a collapse.
        val points = MetricShape.points(listOf(12.0, 12.0, 12.0), ceiling = 100.0)
        val middles = points.map { it.second }.toSet()
        assertEquals(1, middles.size)
        assertEquals((MetricShape.TOP + MetricShape.FLOOR) / 2, middles.first(), 0.001)
    }

    @Test
    fun `one sample is a level line across the cell`() {
        val points = MetricShape.points(listOf(41.0), ceiling = 100.0)
        assertEquals(2, points.size)
        assertEquals(points[0].second, points[1].second, 0.001)
        assertEquals(MetricShape.SIDE, points[0].first, 0.001)
        assertEquals(100.0 - MetricShape.SIDE, points[1].first, 0.001)
    }

    @Test
    fun `no samples draw nothing`() {
        assertTrue(MetricShape.points(emptyList(), ceiling = 100.0).isEmpty())
    }

    @Test
    fun `the window fills the box from side to side`() {
        // Twenty samples span the whole cell, so two cards' strips cover the same
        // span of time and can be compared by eye.
        val points = MetricShape.points((1..20).map { it.toDouble() }, ceiling = 100.0)
        assertEquals(MetricShape.SIDE, points.first().first, 0.001)
        assertEquals(100.0 - MetricShape.SIDE, points.last().first, 0.001)
    }

    @Test
    fun `a window outside its capacity is clamped to the box rather than drawn off it`() {
        val points = MetricShape.points(listOf(-5.0, 140.0), ceiling = 100.0)
        assertTrue(points.all { it.second in MetricShape.TOP..MetricShape.FLOOR })
    }

    @Test
    fun `cores are said without a decimal that is not there`() {
        assertEquals("4", MetricShape.cores(4.0))
        assertEquals("1.5", MetricShape.cores(1.5))
        assertEquals("32", MetricShape.cores(32.0))
        // Nothing said is nothing drawn, not "0 cores".
        assertEquals(null, MetricShape.cores(null))
        assertEquals(null, MetricShape.cores(0.0))
    }
}
