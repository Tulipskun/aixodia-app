package com.tulipskun.aixodia.term

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shapes a panel and an image are drawn from. What matters is geometry: a
 * line has to reach the cell's edge so the next cell's line meets it, a shape
 * has to stay inside its own cell, and a block has to cover the part of the cell
 * the character names — that is the whole reason these are not drawn by a font.
 */
class CellArtTest {

    private val cw = 9f
    private val ch = 18f

    private fun art(cp: Int): List<CellArt.Fill>? = CellArt.of(cp, cw, ch)

    private fun List<CellArt.Fill>.mustBeInsideTheCell() {
        for (f in this) {
            assertTrue("left ${f.left}", f.left >= 0f)
            assertTrue("top ${f.top}", f.top >= 0f)
            assertTrue("right ${f.right}", f.right <= cw + 0.01f)
            assertTrue("bottom ${f.bottom}", f.bottom <= ch + 0.01f)
            assertTrue("empty rect $f", f.right > f.left && f.bottom > f.top)
        }
    }

    @Test
    fun plainTextIsLeftToTheFont() {
        assertNull(art('A'.code))
        assertNull(art(0x2026)) // …
        assertNull(art(0x2571)) // ╱ stays a glyph: a diagonal is not a rectangle
    }

    @Test
    fun everyCharacterOfTheBoxBlockHasAShape() {
        for (cp in 0x2500..0x257F) {
            if (cp in 0x2571..0x2573) continue
            val fills = art(cp)
            assertNotNull("U+%04X".format(cp), fills)
            fills!!.mustBeInsideTheCell()
        }
    }

    @Test
    fun horizontalLineReachesBothEdges() {
        val fills = art(0x2500)!!
        assertEquals(1, fills.size)
        assertEquals(0f, fills[0].left, 0.001f)
        assertEquals(cw, fills[0].right, 0.001f)
        // ...and is one thin band across the middle of the row.
        assertTrue(fills[0].top > ch / 4f && fills[0].bottom < ch * 3f / 4f)
    }

    @Test
    fun verticalLineReachesBothEdges() {
        val fills = art(0x2502)!!
        assertEquals(1, fills.size)
        assertEquals(0f, fills[0].top, 0.001f)
        assertEquals(ch, fills[0].bottom, 0.001f)
        assertTrue(fills[0].left > cw / 4f && fills[0].right < cw * 3f / 4f)
    }

    @Test
    fun cornerStopsNearTheCentreSoItDoesNotRunIntoTheNextCell() {
        // The band reaches the middle of the cell and no further: enough to meet
        // the other half of the corner, not enough to draw in the next cell.
        val topLeft = art(0x250C)!! // ┌
        assertTrue(topLeft.none { it.left <= 0f })
        assertTrue(topLeft.none { it.top <= 0f })
        assertTrue(topLeft.any { it.left < cw / 2f })
        assertTrue(topLeft.any { it.top < ch / 2f })
        val bottomRight = art(0x2518)!! // ┘
        assertTrue(bottomRight.none { it.right >= cw })
        assertTrue(bottomRight.none { it.bottom >= ch })
        assertTrue(bottomRight.any { it.right > cw / 2f })
        assertTrue(bottomRight.any { it.bottom > ch / 2f })
    }

    @Test
    fun aDoubleLineIsTwoBandsPerSide() {
        val horizontal = art(0x2550)!!
        assertEquals(2, horizontal.size)
        // Both bands span the cell, and they are separated, not stacked apart.
        assertEquals(0f, horizontal[0].left, 0.001f)
        assertEquals(cw, horizontal[1].right, 0.001f)
        assertTrue(horizontal[0].bottom < horizontal[1].top)
    }

    @Test
    fun fullBlockCoversTheWholeCell() {
        val fills = art(0x2588)!!
        assertEquals(1, fills.size)
        assertEquals(0f, fills[0].left, 0.001f)
        assertEquals(0f, fills[0].top, 0.001f)
        assertEquals(cw, fills[0].right, 0.001f)
        assertEquals(ch, fills[0].bottom, 0.001f)
    }

    @Test
    fun halfBlocksTilesTheCellExactlyOnce() {
        val upper = art(0x2580)!!.single()
        val lower = art(0x2584)!!.single()
        assertEquals(0f, upper.top, 0.001f)
        assertEquals(ch / 2f, upper.bottom, 0.001f)
        assertEquals(ch / 2f, lower.top, 0.001f)
        assertEquals(ch, lower.bottom, 0.001f)
        // No seam between them: the picture a program paints with them is whole.
        assertEquals(upper.bottom, lower.top, 0.001f)
    }

    @Test
    fun eighthBlocksCoverTheirShareOfTheCell() {
        for (n in 1..7) {
            val lower = art(0x2580 + n)!! // ▁..▇
            assertEquals(ch * (8 - n) / 8f, lower.single().top, 0.001f)
            assertEquals(ch, lower.single().bottom, 0.001f)
            val left = art(0x2588 + n)!! // ▉..▏
            assertEquals(cw * n / 8f, left.single().right, 0.001f)
        }
    }

    @Test
    fun quadrantsFillOnlyTheirCorners() {
        val upperLeft = art(0x2598)!!.single()
        assertEquals(0f, upperLeft.left, 0.001f)
        assertEquals(0f, upperLeft.top, 0.001f)
        assertEquals(cw / 2f, upperLeft.right, 0.001f)
        assertEquals(ch / 2f, upperLeft.bottom, 0.001f)

        val leftHalfPlusLowerRight = art(0x2599)!! // ▙
        assertEquals(
            cw * ch * 0.75f,
            leftHalfPlusLowerRight.sumOf { ((it.right - it.left) * (it.bottom - it.top)).toDouble() }.toFloat(),
            0.01f,
        )
        leftHalfPlusLowerRight.mustBeInsideTheCell()
    }

    @Test
    fun shadesAreTheSameCellOfLighterInk() {
        assertEquals(0.25f, art(0x2591)!!.single().alpha, 0.001f)
        assertEquals(0.5f, art(0x2592)!!.single().alpha, 0.001f)
        assertEquals(0.75f, art(0x2593)!!.single().alpha, 0.001f)
    }

    @Test
    fun aSizeThatIsNotDrawnHasNoShape() {
        assertNull(CellArt.of(0x2500, 0f, 18f))
        assertNull(CellArt.of(0x2500, 9f, 0f))
    }
}
