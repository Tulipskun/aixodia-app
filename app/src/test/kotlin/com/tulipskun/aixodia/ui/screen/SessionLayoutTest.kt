package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the rail may sit.
 *
 * The numbers are the viewer's own: a 54dp rail, a 12dp margin, and a phone's
 * 1080×2340 pixel screen at 2.75 density. The rule being checked is the one a
 * thumb depends on — the rail can be dragged anywhere it fits, and never
 * anywhere it does not.
 */
class SessionLayoutTest {
    private val rail = 54f * 2.75f
    private val margin = 12f * 2.75f
    private val width = 1080f
    private val height = 2340f

    private fun clamp(x: Float, y: Float) = SessionLayout.clamp(x, y, rail, width, height, margin)

    @Test
    fun homeIsTheBottomRightCornerInsetByTheMargin() {
        val (x, y) = SessionLayout.home(rail, width, height, margin)
        assertEquals(width - rail - margin, x, 0.01f)
        assertEquals(height - rail - margin, y, 0.01f)
    }

    @Test
    fun aDragInsideTheScreenIsLeftWhereTheFingerPutIt() {
        val (x, y) = clamp(400f, 900f)
        assertEquals(400f, x, 0.01f)
        assertEquals(900f, y, 0.01f)
    }

    @Test
    fun aDragPastTheRightEdgeStopsAtTheMargin() {
        val (x, _) = clamp(width + 500f, 900f)
        assertEquals(width - rail - margin, x, 0.01f)
    }

    @Test
    fun aDragPastTheLeftEdgeStopsAtTheMargin() {
        val (x, _) = clamp(-900f, 900f)
        assertEquals(margin, x, 0.01f)
    }

    @Test
    fun aDragPastTheBottomStopsAtTheMargin() {
        val (_, y) = clamp(400f, height + 900f)
        assertEquals(height - rail - margin, y, 0.01f)
    }

    @Test
    fun aDragPastTheTopStopsAtTheMargin() {
        val (_, y) = clamp(400f, -900f)
        assertEquals(margin, y, 0.01f)
    }

    @Test
    fun aScreenSmallerThanTheRailStillLeavesItOnScreen() {
        // A windowed session on a tablet, dragged into a corner narrower than
        // the rail plus its margins: the corner wins over the margin, because a
        // rail pinned off-screen cannot be pressed at all.
        val (x, y) = SessionLayout.clamp(0f, 0f, rail = 200f, width = 150f, height = 120f, margin = 30f)
        assertEquals(30f, x, 0.01f)
        assertEquals(30f, y, 0.01f)
    }

    @Test
    fun theGeometryIsTheViewerS() {
        // Read from RealVNC Viewer 4.9.5's resources: the rail is 54dp, the pill
        // 260dp, an item 44dp and the outline 2.5dp.
        assertEquals(54f, SessionLayout.RAIL_DP)
        assertEquals(260f, SessionLayout.PILL_DP)
        assertEquals(44f, SessionLayout.ITEM_DP)
        assertEquals(2.5f, SessionLayout.STROKE_DP)
    }
}
