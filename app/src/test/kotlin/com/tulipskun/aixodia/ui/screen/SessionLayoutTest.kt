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
    fun anOpenPaneBringsTheRailWhereThePaneFits() {
        // 900 px wide, a 152 px rail, a 731 px capsule and a 34 px margin. A rail
        // parked in the top-left corner would show three quarters of the capsule
        // off the left edge and the whole of the pane above the glass, which is
        // what the phone showed before this.
        val rail = 152f
        val pill = 731f
        val width = 900f
        val height = 1800f
        val margin = 34f
        val (x, y) = SessionLayout.openAt(
            x = margin,
            y = margin,
            rail = rail,
            pill = pill,
            width = width,
            height = height,
            margin = margin,
        )
        // The capsule is on the screen: it hangs left from the rail, so its left
        // edge lands on the margin.
        assertEquals(margin, x + rail - pill, 0.5f)
        // And the pane opens downward-room enough to be seen.
        assertEquals(height - rail - margin, y, 0.5f)
    }

    @Test
    fun aRailParkedWhereAPaneFitsIsLeftAlone() {
        // In the bottom-right corner the panes already fit, so nothing moves: a
        // rail that jumped when a pane opened would be a rail that cannot be
        // aimed at.
        val rail = 152f
        val (x, y) = SessionLayout.openAt(
            x = 700f,
            y = 1500f,
            rail = rail,
            pill = 731f,
            width = 900f,
            height = 1800f,
            margin = 34f,
        )
        assertEquals(700f, x, 0.01f)
        assertEquals(1500f, y, 0.01f)
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
