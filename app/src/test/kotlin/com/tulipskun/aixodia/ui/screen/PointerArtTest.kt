package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shape of the arrow, held to what makes it look like one.
 *
 * It was drawn by hand inside the view, and what came out had a tail outside its
 * own box and a rim nearly a fifth of its height — at fourteen pixels that is a
 * blob, not a pointer. Shape as numbers can be checked; shape inside an
 * `onDraw` cannot.
 */
class PointerArtTest {

    @Test
    fun the_tip_is_on_the_pointer() {
        // The arrow must hang from the point it stands for: nothing above or to
        // the left of the tip, or taps click somewhere the arrow is not.
        val (tipX, tipY) = PointerArt.OUTLINE.first()
        assertEquals(0f, tipX, 0f)
        assertEquals(0f, tipY, 0f)
        assertTrue(PointerArt.OUTLINE.all { it.first >= 0f && it.second >= 0f })
    }

    @Test
    fun the_arrow_fits_its_own_box() {
        val tallest = PointerArt.OUTLINE.maxOf { it.second }
        assertEquals(1f, tallest, 0.0001f)
        val widest = PointerArt.OUTLINE.maxOf { it.first }
        assertTrue("too wide to read as an arrow: $widest", widest < 0.8f)
    }

    @Test
    fun the_rim_stays_in_proportion_to_the_sprite() {
        // A rim of 2.5 around a 14 pixel arrow is most of the arrow.
        assertTrue(PointerArt.rim(PointerArt.SIZE) < PointerArt.SIZE * 0.15f)
        assertTrue(PointerArt.rim(PointerArt.MAX_SIZE) > PointerArt.rim(PointerArt.SIZE))
    }

    @Test
    fun a_shrunken_picture_does_not_shrink_the_arrow_away() {
        // The desktop is drawn at 70% on most phones; an arrow scaled with it
        // becomes a speck.
        assertEquals(PointerArt.SIZE, PointerArt.size(0.7f, 0.7f), 0f)
        assertEquals(PointerArt.SIZE, PointerArt.size(1f, 1f), 0f)
        assertEquals(PointerArt.MAX_SIZE, PointerArt.size(9f, 9f), 0f)
        assertTrue(PointerArt.size(2f, 2f) > PointerArt.SIZE)
    }

    @Test
    fun a_cursor_bitmap_hangs_its_hot_spot_on_the_pointer() {
        // Measured from the engine's display: the arrow is a 24x24 cursor whose
        // hot spot is (3, 1), and an xterm's I-beam is 9x16 with its hot spot in
        // the middle of its top edge. The pixel that clicks must be the pixel
        // under the hot spot, or every tap lands a few pixels away.
        val arrow = PointerArt.bitmapRect(
            pointerX = 400f,
            pointerY = 300f,
            width = 24,
            height = 24,
            hotX = 3,
            hotY = 1,
            scale = 1f,
        )
        assertEquals(400f - 3, arrow.left, 0f)
        assertEquals(300f - 1, arrow.top, 0f)
        assertEquals(24f, arrow.width, 0f)

        val iBeam = PointerArt.bitmapRect(400f, 300f, 9, 16, 4, 8, 1f)
        assertEquals(396f, iBeam.left, 0f)
        assertEquals(292f, iBeam.top, 0f)
    }

    @Test
    fun a_zoomed_picture_grows_the_cursor_and_a_fitted_one_does_not_shrink_it() {
        // A fitted desktop is drawn at about 70%, which would make a 24 pixel
        // cursor 17 pixels of smudge.
        val shrunk = PointerArt.bitmapRect(0f, 0f, 24, 24, 0, 0, 0.7f)
        assertEquals(24f, shrunk.width, 0f)
        val zoomed = PointerArt.bitmapRect(0f, 0f, 24, 24, 0, 0, 2f)
        assertEquals(48f, zoomed.width, 0f)
        assertEquals(48f, zoomed.height, 0f)
    }
}
