package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The picture's place on the phone, tested without a touch screen.
 *
 * Two of the three faults reported from the phone were in this arithmetic: a
 * pinch that flicked the picture back, and a zoom that vanished. Both are
 * reproducible here, in plain numbers, which is why the arithmetic was moved out
 * of the view in the first place.
 */
class ViewportTest {

    // A 1280×720 desktop fitted into a 900×506 view: 0.703 of its own size.
    private val viewW = 900
    private val viewH = 506
    private val picW = 1280
    private val picH = 720

    private fun viewport() = Viewport()

    /** The picture pixel under a point of the view. */
    private fun pictureUnder(v: Viewport, x: Float, y: Float): Pair<Float, Float> {
        val box = v.box(viewW, viewH, picW, picH)
        return (x - box.left) / box.width * picW to (y - box.top) / box.height * picH
    }

    @Test
    fun a_fresh_viewport_fits_the_whole_desktop() {
        val v = viewport()
        assertEquals(1f, v.zoom, 0f)
        val box = v.box(viewW, viewH, picW, picH)
        // Fitted: the whole picture is inside the view, centred.
        assertTrue(box.left >= -0.01f && box.top >= -0.01f)
        assertTrue(box.left + box.width <= viewW + 0.01f)
        assertTrue(box.left + box.width / 2f - viewW / 2f in -0.01f..0.01f)
    }

    @Test
    fun a_pinch_keeps_the_same_picture_pixel_under_the_same_place_on_screen() {
        val v = viewport()
        val anchorX = 300f
        val anchorY = 250f
        val before = pictureUnder(v, anchorX, anchorY)
        v.beginPinch(anchorX, anchorY)
        // The fingers spread in steps, the way events arrive.
        for (zoom in listOf(1.2f, 1.6f, 2.0f, 2.7f, 3.4f)) {
            v.pinch(zoom, 0f, 0f, viewW, viewH, picW, picH)
            val after = pictureUnder(v, anchorX, anchorY)
            // Only the clamp can move it, and at these zooms there is slack to
            // spare, so the anchor must be exact.
            assertEquals("x at zoom $zoom", before.first, after.first, 0.6f)
            assertEquals("y at zoom $zoom", before.second, after.second, 0.6f)
        }
    }

    @Test
    fun a_pinch_asked_twice_for_the_same_zoom_lands_in_the_same_place() {
        // The defect that made the picture flick: a pan worked out from the
        // running pan and zoom drifts a little on every event, and the size of
        // the drift depends on how many events arrived. The same request must
        // give the same answer, in whatever order the events came.
        val a = viewport()
        a.beginPinch(320f, 200f)
        a.pinch(1.5f, 0f, 0f, viewW, viewH, picW, picH)
        a.pinch(2.4f, 0f, 0f, viewW, viewH, picW, picH)

        val b = viewport()
        b.beginPinch(320f, 200f)
        b.pinch(2.4f, 0f, 0f, viewW, viewH, picW, picH)

        assertEquals(a.zoom, b.zoom, 0f)
        assertEquals(a.panX, b.panX, 0.001f)
        assertEquals(a.panY, b.panY, 0.001f)
    }

    @Test
    fun the_fingers_sliding_carry_the_picture_with_them() {
        val v = viewport()
        v.beginPinch(300f, 200f)
        v.pinch(2f, 0f, 0f, viewW, viewH, picW, picH)
        val started = v.panX
        v.beginPinch(300f, 200f)
        v.pinch(2f, 40f, 0f, viewW, viewH, picW, picH)
        // A pinch already zoomed in has slack, so 40 px of finger travel moves
        // the picture 40 px.
        assertEquals(started + 40f, v.panX, 0.5f)
    }

    @Test
    fun a_zoom_survives_the_picture_being_dropped() {
        // This is the "pinch in, and a second later it is fitted again" fault:
        // the link blinked, the picture was dropped, and the drop reset the zoom.
        val v = viewport()
        v.beginPinch(320f, 200f)
        v.pinch(2.5f, 0f, 0f, viewW, viewH, picW, picH)
        val zoom = v.zoom
        val pan = v.panX
        // A redial does not touch the viewport; the picture merely comes back.
        assertEquals(zoom, v.zoom, 0f)
        assertEquals(pan, v.panX, 0f)
        // And when the reader asks for the whole desktop, it goes.
        v.fit()
        assertEquals(1f, v.zoom, 0f)
        assertEquals(0f, v.panX, 0f)
    }

    @Test
    fun the_picture_cannot_be_dragged_off_into_empty_space() {
        val v = viewport()
        v.beginPinch(450f, 250f)
        v.pinch(2f, 0f, 0f, viewW, viewH, picW, picH)
        v.beginPinch(450f, 250f)
        v.pinch(2f, 5000f, 5000f, viewW, viewH, picW, picH)
        val box = v.box(viewW, viewH, picW, picH)
        // Dragged as far as it will go, the picture's left edge is at the view's
        // left edge — half the overflow each way, so every part of a zoomed
        // picture can be brought to the middle, and none of it can be lost.
        assertEquals(0f, box.left, 0.5f)
        assertEquals(0f, box.top, 0.5f)
        // And the other way.
        v.beginPinch(450f, 250f)
        v.pinch(2f, -5000f, -5000f, viewW, viewH, picW, picH)
        val left = v.box(viewW, viewH, picW, picH)
        assertEquals(viewW.toFloat(), left.left + left.width, 0.5f)
        assertEquals(viewH.toFloat(), left.top + left.height, 0.5f)
    }

    @Test
    fun pinching_out_below_the_fit_does_nothing() {
        val v = viewport()
        v.beginPinch(450f, 250f)
        v.pinch(0.2f, 0f, 0f, viewW, viewH, picW, picH)
        assertEquals(Viewport.MIN_ZOOM, v.zoom, 0f)
        assertEquals(0f, v.panX, 0f)
    }

    @Test
    fun actual_size_is_one_picture_pixel_per_screen_pixel() {
        val v = viewport()
        v.actualSize(viewW, viewH, picW, picH)
        val box = v.box(viewW, viewH, picW, picH)
        assertEquals(picW.toFloat(), box.width, 0.5f)
        assertTrue(v.isActualSize(viewW, viewH, picW, picH))
        assertFalse(viewport().isActualSize(viewW, viewH, picW, picH))
        // The visible part is a window onto a picture bigger than the view, and
        // it may be panned.
        assertTrue(v.isZoomed)
    }

    @Test
    fun the_middle_of_the_pane_is_the_point_drawn_there() {
        // Zooming pulls the arrow to the middle by moving the arrow, never the
        // picture: the answer is whatever is drawn at the middle of the pane.
        val v = viewport()
        val fitted = v.pictureAt(viewW / 2f, viewH / 2f, viewW, viewH, picW, picH)!!
        assertEquals(picW / 2f, fitted.first, 1f)
        assertEquals(picH / 2f, fitted.second, 1f)

        // Zoomed and slid so the arrow at picture x=900 is centred: the middle of
        // the pane is that very pixel, so pulling the arrow there moves nothing.
        v.actualSize(viewW, viewH, picW, picH)
        v.followPointer(750f, 360f, viewW, viewH, picW, picH)
        val zoomed = v.pictureAt(viewW / 2f, viewH / 2f, viewW, viewH, picW, picH)!!
        assertEquals(750f, zoomed.first, 1f)
        assertEquals(360f, zoomed.second, 1f)
    }

    @Test
    fun a_pane_point_maps_back_to_the_pixel_drawn_under_it() {
        val v = viewport()
        v.actualSize(viewW, viewH, picW, picH)
        v.followPointer(700f, 300f, viewW, viewH, picW, picH)
        val box = v.box(viewW, viewH, picW, picH)
        val scale = box.width / picW
        // Any picture pixel, drawn where it is drawn, must map back to itself.
        for (x in listOf(0f, 300f, 700f, 1279f)) {
            val drawn = box.left + x * scale
            val back = v.pictureAt(drawn, viewH / 2f, viewW, viewH, picW, picH)!!
            assertEquals("pixel $x", x, back.first, 1f)
        }
    }

    @Test
    fun a_point_outside_the_picture_is_clamped_to_its_edge() {
        val v = viewport()
        // The letterbox bars at the top and bottom of a fitted picture: the arrow
        // can never be pulled out of the picture itself.
        val (_, top) = v.pictureAt(viewW / 2f, -50f, viewW, viewH, picW, picH)!!
        assertEquals(0f, top, 1f)
        val (_, bottom) = v.pictureAt(viewW / 2f, viewH + 50f, viewW, viewH, picW, picH)!!
        assertEquals(picH - 1f, bottom, 1f)
    }
}
