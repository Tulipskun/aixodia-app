package com.tulipskun.aixodia.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun a_text_cursor_is_centred_on_the_pointer() {
        // A text cursor has no tip to hang on the pointer, so its bar is split by
        // it: the place the click lands is the place the bar is drawn.
        val bars = PointerArt.textBars(x = 400f, y = 300f, size = 16f)
        val stem = bars.first()
        assertEquals(400f, stem.left + stem.width / 2f, 0.001f)
        assertEquals(300f, stem.top + stem.height / 2f, 0.001f)
        assertEquals(16f, stem.height, 0.001f)
    }

    @Test
    fun a_text_cursor_has_a_serif_at_each_end() {
        val bars = PointerArt.textBars(x = 0f, y = 0f, size = 16f)
        val (stem, top, bottom) = bars
        // Two serifs, the same width, the height of one stroke, one at each end.
        assertEquals(top.width, bottom.width, 0.001f)
        assertEquals(PointerArt.textStroke(16f), top.height, 0.001f)
        assertEquals(stem.top, top.top, 0.001f)
        assertEquals(stem.top + stem.height, bottom.top + bottom.height, 0.001f)
        // Narrower than it is tall, or it reads as a cross rather than a cursor.
        assertTrue(top.width < stem.height)
        assertTrue(PointerArt.textStroke(16f) >= 1.6f)
    }
}
