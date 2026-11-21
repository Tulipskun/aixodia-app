package com.tulipskun.aixodia.term

import kotlin.math.max
import kotlin.math.min

/**
 * The code points a program draws a *shape* with: the box-drawing block, the
 * block elements and the shades.
 *
 * These must fill the cell, not the glyph. A program builds a panel out of
 * `╭─╮│╰─╯` expecting every cell's line to meet its neighbour's exactly, and a
 * renderer that hands the character to the font gets whatever that font has:
 * the monospace family covers Latin, so `─` arrives from a fallback face whose
 * advance and stroke are nobody's cell. The result is the panel a reader sees as
 * broken rules with gaps between them, and half-block art — how a CLI paints a
 * screenshot or a QR code — as stripes with a seam per row.
 *
 * So the shapes are geometry here, in pixels relative to the cell's top-left,
 * and [TerminalView] fills them with the cell's own colour. Nothing depends on
 * the font, and the tiling is exact at any size.
 */
object CellArt {

    /** One rectangle to fill, relative to the cell's top-left corner. */
    data class Fill(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        /** 1 for a solid shape; a shade is lighter ink over the same cell. */
        val alpha: Float = 1f,
    )

    /**
     * The shapes [codePoint] draws in a cell of [cellWidth] by [cellHeight], or
     * null when the character is an ordinary glyph and the font should draw it.
     */
    fun of(codePoint: Int, cellWidth: Float, cellHeight: Float): List<Fill>? {
        if (cellWidth <= 0f || cellHeight <= 0f) return null
        block(codePoint, cellWidth, cellHeight)?.let { return it }
        if (codePoint !in FIRST_BOX..LAST_BOX) return null
        val spec = SPECS[codePoint - FIRST_BOX]
        if (spec == NOTHING) return null
        return lines(spec, cellWidth, cellHeight)
    }

    // =======================================================================
    // lines
    // =======================================================================

    private const val NOTHING = 0
    private const val LIGHT = 1
    private const val HEAVY = 2
    private const val DOUBLE = 3

    private const val DASHED = 0x100

    /** The four directions, each two bits of [SPECS]. */
    private fun up(spec: Int) = spec and 0x03
    private fun down(spec: Int) = (spec shr 2) and 0x03
    private fun left(spec: Int) = (spec shr 4) and 0x03
    private fun right(spec: Int) = (spec shr 6) and 0x03

    private fun spec(up: Int, down: Int, left: Int, right: Int, dashed: Boolean = false): Int =
        up or (down shl 2) or (left shl 4) or (right shl 6) or (if (dashed) DASHED else 0)

    /**
     * A cell's line, drawn as one band per direction.
     *
     * Each half of the cell reaches the centre and a little past it, so a line
     * that turns — or meets another cell's line — meets it in ink rather than in
     * a seam. The half that has no neighbour stops at the centre, which is what
     * makes `╰` an L and not a line running on into the next cell.
     */
    private fun lines(spec: Int, cw: Float, ch: Float): List<Fill> {
        // One thickness for both axes: the cell is taller than it is wide, and a
        // line measured per axis would come out visibly heavier one way.
        val thin = max(1f, min(cw, ch) * 0.10f)
        val cx = cw / 2f
        val cy = ch / 2f
        val dashed = spec and DASHED != 0
        val fills = ArrayList<Fill>(8)

        fun thickness(weight: Int) = if (weight == DOUBLE) thin * 0.8f else thin
        fun offsets(weight: Int): FloatArray =
            if (weight == DOUBLE) {
                val t = thickness(DOUBLE)
                val gap = thin * 0.7f
                floatArrayOf(-(t + gap) / 2f, (t + gap) / 2f)
            } else {
                floatArrayOf(0f)
            }

        fun horizontal(weight: Int, x0: Float, x1: Float) {
            if (weight == NOTHING) return
            val t = thickness(weight)
            for (off in offsets(weight)) {
                val y0 = max(0f, cy + off - t / 2f)
                val y1 = min(ch, cy + off + t / 2f)
                if (dashed) {
                    val dash = (x1 - x0) * 0.45f
                    fills.add(Fill(x0, y0, x0 + dash, y1))
                    fills.add(Fill(x1 - dash, y0, x1, y1))
                } else {
                    fills.add(Fill(x0, y0, x1, y1))
                }
            }
        }

        fun vertical(weight: Int, y0: Float, y1: Float) {
            if (weight == NOTHING) return
            val t = thickness(weight)
            for (off in offsets(weight)) {
                val x0 = max(0f, cx + off - t / 2f)
                val x1 = min(cw, cx + off + t / 2f)
                if (dashed) {
                    val dash = (y1 - y0) * 0.45f
                    fills.add(Fill(x0, y0, x1, y0 + dash))
                    fills.add(Fill(x0, y1 - dash, x1, y1))
                } else {
                    fills.add(Fill(x0, y0, x1, y1))
                }
            }
        }

        // One band when both halves are the same line: 54 cells of `─` are 54
        // bands that meet exactly at the cell edge, not 108 that overlap.
        val lw = left(spec)
        val rw = right(spec)
        if (lw != NOTHING && lw == rw) {
            horizontal(lw, 0f, cw)
        } else {
            if (lw != NOTHING) horizontal(lw, 0f, cx + thickness(lw) / 2f)
            if (rw != NOTHING) horizontal(rw, cx - thickness(rw) / 2f, cw)
        }
        val uw = up(spec)
        val dw = down(spec)
        if (uw != NOTHING && uw == dw) {
            vertical(uw, 0f, ch)
        } else {
            if (uw != NOTHING) vertical(uw, 0f, cy + thickness(uw) / 2f)
            if (dw != NOTHING) vertical(dw, cy - thickness(dw) / 2f, ch)
        }
        return fills
    }

    // =======================================================================
    // blocks, shades and quadrants
    // =======================================================================

    /**
     * The block elements: the eighth blocks a progress bar or a spinner is built
     * from, and the half and quadrant blocks an image is painted with — `▀` over
     * a background colour is how a terminal shows a picture at two rows per cell.
     */
    private fun block(cp: Int, cw: Float, ch: Float): List<Fill>? {
        val eighth = ch / 8f
        val eighthX = cw / 8f
        val halfW = cw / 2f
        val halfH = ch / 2f
        return when (cp) {
            // ▀ upper half, ▁..▇ lower eighths, █ full.
            0x2580 -> listOf(Fill(0f, 0f, cw, halfH))
            in 0x2581..0x2587 -> {
                val step = (cp - 0x2580) * eighth
                listOf(Fill(0f, ch - step, cw, ch))
            }
            0x2588 -> listOf(Fill(0f, 0f, cw, ch))
            // ▉..▏ left eighths, ▐ right half.
            in 0x2589..0x258F -> listOf(Fill(0f, 0f, (cp - 0x2588) * eighthX, ch))
            0x2590 -> listOf(Fill(halfW, 0f, cw, ch))
            // ░▒▓ shades.
            0x2591 -> listOf(Fill(0f, 0f, cw, ch, 0.25f))
            0x2592 -> listOf(Fill(0f, 0f, cw, ch, 0.5f))
            0x2593 -> listOf(Fill(0f, 0f, cw, ch, 0.75f))
            // ▔ upper eighth, ▕ right eighth.
            0x2594 -> listOf(Fill(0f, 0f, cw, eighth))
            0x2595 -> listOf(Fill(cw - eighthX, 0f, cw, ch))
            // Quadrants.
            0x2596 -> listOf(Fill(0f, halfH, halfW, ch))
            0x2597 -> listOf(Fill(halfW, halfH, cw, ch))
            0x2598 -> listOf(Fill(0f, 0f, halfW, halfH))
            0x2599 -> listOf(Fill(0f, 0f, halfW, ch), Fill(halfW, halfH, cw, ch))
            0x259A -> listOf(Fill(0f, 0f, halfW, halfH), Fill(halfW, halfH, cw, ch))
            0x259B -> listOf(Fill(0f, 0f, cw, halfH), Fill(0f, halfH, halfW, ch))
            0x259C -> listOf(Fill(0f, 0f, cw, halfH), Fill(halfW, halfH, cw, ch))
            0x259D -> listOf(Fill(halfW, 0f, cw, halfH))
            0x259E -> listOf(Fill(halfW, 0f, cw, halfH), Fill(0f, halfH, halfW, ch))
            0x259F -> listOf(Fill(halfW, 0f, cw, ch), Fill(0f, halfH, halfW, ch))
            else -> null
        }
    }

    private const val FIRST_BOX = 0x2500
    private const val LAST_BOX = 0x257F

    /**
     * U+2500..U+257F, four directions of two bits each (up, down, left, right)
     * plus [DASHED]. The diagonals (U+2571..U+2573) stay out: a shape the font
     * draws is better than a rectangle pretending to be a slash.
     */
    private val SPECS = IntArray(LAST_BOX - FIRST_BOX + 1).also { t ->
        fun put(cp: Int, spec: Int) {
            t[cp - FIRST_BOX] = spec
        }
        // ─ ━ │ ┃ and their dashed forms.
        put(0x2500, spec(0, 0, LIGHT, LIGHT))
        put(0x2501, spec(0, 0, HEAVY, HEAVY))
        put(0x2502, spec(LIGHT, LIGHT, 0, 0))
        put(0x2503, spec(HEAVY, HEAVY, 0, 0))
        put(0x2504, spec(0, 0, LIGHT, LIGHT, dashed = true))
        put(0x2505, spec(0, 0, HEAVY, HEAVY, dashed = true))
        put(0x2506, spec(LIGHT, LIGHT, 0, 0, dashed = true))
        put(0x2507, spec(HEAVY, HEAVY, 0, 0, dashed = true))
        put(0x2508, spec(0, 0, LIGHT, LIGHT, dashed = true))
        put(0x2509, spec(0, 0, HEAVY, HEAVY, dashed = true))
        put(0x250A, spec(LIGHT, LIGHT, 0, 0, dashed = true))
        put(0x250B, spec(HEAVY, HEAVY, 0, 0, dashed = true))
        // ┌ ┍ ┎ ┏
        put(0x250C, spec(0, LIGHT, 0, LIGHT))
        put(0x250D, spec(0, HEAVY, 0, LIGHT))
        put(0x250E, spec(0, LIGHT, 0, HEAVY))
        put(0x250F, spec(0, HEAVY, 0, HEAVY))
        // ┐ ┑ ┒ ┓
        put(0x2510, spec(0, LIGHT, LIGHT, 0))
        put(0x2511, spec(0, HEAVY, LIGHT, 0))
        put(0x2512, spec(0, LIGHT, HEAVY, 0))
        put(0x2513, spec(0, HEAVY, HEAVY, 0))
        // └ ┕ ┖ ┗
        put(0x2514, spec(LIGHT, 0, 0, LIGHT))
        put(0x2515, spec(HEAVY, 0, 0, LIGHT))
        put(0x2516, spec(LIGHT, 0, 0, HEAVY))
        put(0x2517, spec(HEAVY, 0, 0, HEAVY))
        // ┘ ┙ ┚ ┛
        put(0x2518, spec(LIGHT, 0, LIGHT, 0))
        put(0x2519, spec(HEAVY, 0, LIGHT, 0))
        put(0x251A, spec(LIGHT, 0, HEAVY, 0))
        put(0x251B, spec(HEAVY, 0, HEAVY, 0))
        // ├ ┝ ┞ ┟ ┠ ┡ ┢ ┣
        put(0x251C, spec(LIGHT, LIGHT, 0, LIGHT))
        put(0x251D, spec(HEAVY, HEAVY, 0, LIGHT))
        put(0x251E, spec(LIGHT, HEAVY, 0, LIGHT))
        put(0x251F, spec(HEAVY, LIGHT, 0, LIGHT))
        put(0x2520, spec(LIGHT, LIGHT, 0, HEAVY))
        put(0x2521, spec(HEAVY, HEAVY, 0, HEAVY))
        put(0x2522, spec(LIGHT, HEAVY, 0, HEAVY))
        put(0x2523, spec(HEAVY, LIGHT, 0, HEAVY))
        // ┤ ┥ ┦ ┧ ┨ ┩ ┪ ┫
        put(0x2524, spec(LIGHT, LIGHT, LIGHT, 0))
        put(0x2525, spec(HEAVY, HEAVY, LIGHT, 0))
        put(0x2526, spec(LIGHT, HEAVY, LIGHT, 0))
        put(0x2527, spec(HEAVY, LIGHT, LIGHT, 0))
        put(0x2528, spec(LIGHT, LIGHT, HEAVY, 0))
        put(0x2529, spec(HEAVY, HEAVY, HEAVY, 0))
        put(0x252A, spec(LIGHT, HEAVY, HEAVY, 0))
        put(0x252B, spec(HEAVY, LIGHT, HEAVY, 0))
        // ┬ ┭ ┮ ┯ ┰ ┱ ┲ ┳
        put(0x252C, spec(0, LIGHT, LIGHT, LIGHT))
        put(0x252D, spec(0, LIGHT, LIGHT, HEAVY))
        put(0x252E, spec(0, LIGHT, HEAVY, LIGHT))
        put(0x252F, spec(0, LIGHT, HEAVY, HEAVY))
        put(0x2530, spec(0, HEAVY, LIGHT, LIGHT))
        put(0x2531, spec(0, HEAVY, LIGHT, HEAVY))
        put(0x2532, spec(0, HEAVY, HEAVY, LIGHT))
        put(0x2533, spec(0, HEAVY, HEAVY, HEAVY))
        // ┴ ┵ ┶ ┷ ┸ ┹ ┺ ┻
        put(0x2534, spec(LIGHT, 0, LIGHT, LIGHT))
        put(0x2535, spec(LIGHT, 0, LIGHT, HEAVY))
        put(0x2536, spec(LIGHT, 0, HEAVY, LIGHT))
        put(0x2537, spec(LIGHT, 0, HEAVY, HEAVY))
        put(0x2538, spec(HEAVY, 0, LIGHT, LIGHT))
        put(0x2539, spec(HEAVY, 0, LIGHT, HEAVY))
        put(0x253A, spec(HEAVY, 0, HEAVY, LIGHT))
        put(0x253B, spec(HEAVY, 0, HEAVY, HEAVY))
        // ┼ and the mixed crosses.
        put(0x253C, spec(LIGHT, LIGHT, LIGHT, LIGHT))
        put(0x253D, spec(LIGHT, LIGHT, HEAVY, LIGHT))
        put(0x253E, spec(LIGHT, LIGHT, LIGHT, HEAVY))
        put(0x253F, spec(LIGHT, LIGHT, HEAVY, HEAVY))
        put(0x2540, spec(HEAVY, LIGHT, LIGHT, LIGHT))
        put(0x2541, spec(LIGHT, HEAVY, LIGHT, LIGHT))
        put(0x2542, spec(HEAVY, HEAVY, LIGHT, LIGHT))
        put(0x2543, spec(HEAVY, LIGHT, HEAVY, LIGHT))
        put(0x2544, spec(LIGHT, HEAVY, HEAVY, LIGHT))
        put(0x2545, spec(HEAVY, LIGHT, LIGHT, HEAVY))
        put(0x2546, spec(LIGHT, HEAVY, LIGHT, HEAVY))
        put(0x2547, spec(HEAVY, HEAVY, LIGHT, HEAVY))
        put(0x2548, spec(HEAVY, LIGHT, HEAVY, HEAVY))
        put(0x2549, spec(LIGHT, HEAVY, HEAVY, HEAVY))
        put(0x254A, spec(HEAVY, HEAVY, HEAVY, LIGHT))
        put(0x254B, spec(HEAVY, HEAVY, HEAVY, HEAVY))
        // ╌ ╍ ╎ ╏
        put(0x254C, spec(0, 0, LIGHT, LIGHT, dashed = true))
        put(0x254D, spec(0, 0, HEAVY, HEAVY, dashed = true))
        put(0x254E, spec(LIGHT, LIGHT, 0, 0, dashed = true))
        put(0x254F, spec(HEAVY, HEAVY, 0, 0, dashed = true))
        // ═ ║ and the double-line corners and tees.
        put(0x2550, spec(0, 0, DOUBLE, DOUBLE))
        put(0x2551, spec(DOUBLE, DOUBLE, 0, 0))
        put(0x2552, spec(0, LIGHT, 0, DOUBLE))
        put(0x2553, spec(0, DOUBLE, 0, LIGHT))
        put(0x2554, spec(0, DOUBLE, 0, DOUBLE))
        put(0x2555, spec(0, LIGHT, DOUBLE, 0))
        put(0x2556, spec(0, DOUBLE, LIGHT, 0))
        put(0x2557, spec(0, DOUBLE, DOUBLE, 0))
        put(0x2558, spec(LIGHT, 0, 0, DOUBLE))
        put(0x2559, spec(DOUBLE, 0, 0, LIGHT))
        put(0x255A, spec(DOUBLE, 0, 0, DOUBLE))
        put(0x255B, spec(LIGHT, 0, DOUBLE, 0))
        put(0x255C, spec(DOUBLE, 0, LIGHT, 0))
        put(0x255D, spec(DOUBLE, 0, DOUBLE, 0))
        put(0x255E, spec(LIGHT, LIGHT, 0, DOUBLE))
        put(0x255F, spec(DOUBLE, DOUBLE, 0, LIGHT))
        put(0x2560, spec(DOUBLE, DOUBLE, 0, DOUBLE))
        put(0x2561, spec(LIGHT, LIGHT, DOUBLE, 0))
        put(0x2562, spec(DOUBLE, DOUBLE, LIGHT, 0))
        put(0x2563, spec(DOUBLE, DOUBLE, DOUBLE, 0))
        put(0x2564, spec(0, DOUBLE, LIGHT, LIGHT))
        put(0x2565, spec(0, LIGHT, DOUBLE, DOUBLE))
        put(0x2566, spec(0, DOUBLE, DOUBLE, DOUBLE))
        put(0x2567, spec(DOUBLE, 0, LIGHT, LIGHT))
        put(0x2568, spec(LIGHT, 0, DOUBLE, DOUBLE))
        put(0x2569, spec(DOUBLE, 0, DOUBLE, DOUBLE))
        put(0x256A, spec(LIGHT, LIGHT, DOUBLE, DOUBLE))
        put(0x256B, spec(DOUBLE, DOUBLE, LIGHT, LIGHT))
        put(0x256C, spec(DOUBLE, DOUBLE, DOUBLE, DOUBLE))
        // ╭ ╮ ╯ ╰: a rounded corner is drawn square — the joint is in the same
        // place, and a square corner reads as a corner where a font's hairline
        // reads as a break.
        put(0x256D, spec(0, LIGHT, 0, LIGHT))
        put(0x256E, spec(0, LIGHT, LIGHT, 0))
        put(0x256F, spec(LIGHT, 0, LIGHT, 0))
        put(0x2570, spec(LIGHT, 0, 0, LIGHT))
        // ╴ ╵ ╶ ╷ and their heavy, mixed forms: half a line each.
        put(0x2574, spec(0, 0, LIGHT, 0))
        put(0x2575, spec(LIGHT, 0, 0, 0))
        put(0x2576, spec(0, 0, 0, LIGHT))
        put(0x2577, spec(0, LIGHT, 0, 0))
        put(0x2578, spec(0, 0, HEAVY, 0))
        put(0x2579, spec(HEAVY, 0, 0, 0))
        put(0x257A, spec(0, 0, 0, HEAVY))
        put(0x257B, spec(0, HEAVY, 0, 0))
        put(0x257C, spec(0, 0, LIGHT, HEAVY))
        put(0x257D, spec(LIGHT, HEAVY, 0, 0))
        put(0x257E, spec(0, 0, HEAVY, LIGHT))
        put(0x257F, spec(HEAVY, LIGHT, 0, 0))
    }
}
