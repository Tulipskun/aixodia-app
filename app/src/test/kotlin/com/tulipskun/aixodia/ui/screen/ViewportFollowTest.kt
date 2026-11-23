package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the arrow sits in the pane, in plain numbers.
 *
 * Three things were asked for from the phone and all three are this arithmetic:
 * while the picture is zoomed the arrow belongs in the middle of the pane, it
 * only leaves the middle when the picture has nowhere left to slide, and while
 * the keyboard is up the field being typed into belongs just above it.
 */
class ViewportFollowTest {

    // A 1280×720 desktop fitted into a 900×506 view.
    private val viewW = 900
    private val viewH = 506
    private val picW = 1280
    private val picH = 720

    private fun viewport() = Viewport()

    /** Where a picture pixel ends up on the pane, in view pixels. */
    private fun screenOf(v: Viewport, x: Float, y: Float): Pair<Float, Float> {
        val box = v.box(viewW, viewH, picW, picH)
        return (box.left + x * box.width / picW) to (box.top + y * box.height / picH)
    }

    @Test
    fun a_zoom_with_slack_pins_the_arrow_to_the_middle_of_the_pane() {
        val v = viewport()
        v.actualSize(viewW, viewH, picW, picH)
        for (at in listOf(500f to 300f, 640f to 360f, 700f to 420f)) {
            v.followPointer(at.first, at.second, viewW, viewH, picW, picH)
            val (x, y) = screenOf(v, at.first, at.second)
            assertEquals("x for $at", viewW / 2f, x, 1f)
            assertEquals("y for $at", viewH / 2f, y, 1f)
        }
    }

    @Test
    fun at_the_pictures_edge_the_arrow_moves_and_the_picture_stays() {
        val v = viewport()
        v.actualSize(viewW, viewH, picW, picH)
        v.followPointer(0f, 0f, viewW, viewH, picW, picH)
        val (x, y) = screenOf(v, 0f, 0f)
        // The desktop's top-left corner cannot be shown in the middle of the
        // pane, so it is drawn as near to it as the edge permits — on the pane.
        assertTrue("corner drawn at $x,$y", x <= viewW / 2f + 1f && y <= viewH / 2f + 1f)
        assertTrue("and on the pane", x >= 0f && y >= 0f)
    }

    @Test
    fun typing_puts_the_arrow_just_above_the_keyboard() {
        val v = viewport()
        v.actualSize(viewW, viewH, picW, picH)
        val arrow = 640f to 600f
        val (toX, toY) =
            v.panFor(arrow.first, arrow.second, viewW / 2f, viewH - 60f, viewW, viewH, picW, picH)!!
        v.slideTo(toX, toY, viewW, viewH, picW, picH)
        val (x, y) = screenOf(v, arrow.first, arrow.second)
        assertEquals("centred across", viewW / 2f, x, 1f)
        assertEquals("and above the keyboard", viewH - 60f, y, 1f)
    }

    @Test
    fun a_fitted_picture_cannot_be_slid_and_the_arrow_keeps_its_place() {
        val v = viewport()
        // At fit there is no slack, so asking to slide changes nothing: the pane
        // shows the whole desktop and the arrow is drawn where it really is.
        v.slideTo(120f, -80f, viewW, viewH, picW, picH)
        assertEquals(0f, v.panX, 0.01f)
        assertEquals(0f, v.panY, 0.01f)
    }
}
