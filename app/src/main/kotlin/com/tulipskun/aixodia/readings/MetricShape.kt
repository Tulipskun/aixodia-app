package com.tulipskun.aixodia.readings

import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

/**
 * What a reading prints, and the shape behind it — the console's own arithmetic,
 * kept out of the composable so it can be checked without drawing anything.
 *
 * The web draws a machine's card with three readings and a chart under each, and a
 * phone showing the same card has to agree with it down to the last decimal: two
 * screens describing one machine differently is the same kind of bug as a number
 * that is simply wrong.
 */
internal object MetricShape {

    /* The chart's own margins inside the cell, in the 0–100 box the console draws
       in: the line runs close to the label and the reading above it, so it reads as
       theirs rather than as a band behind them. */
    const val TOP = 8.0
    const val FLOOR = 92.0
    const val SIDE = 4.0

    /** How much of the ceiling a window has to move before its shape is drawn. */
    private const val LEAST_SPAN = 0.04

    /**
     * A reading as the card says it: the number, and the unit after it.
     *
     * A metric with a capacity is said as used over whole — `23.1/123 GB` — and one
     * without as a share — `37%`. Nothing sampled yet is the console's em dash, not
     * a zero, which would be a claim the engine never made.
     */
    fun reading(series: List<Double>, digits: Int, unit: String, total: String?): Pair<String, String> {
        val latest = series.lastOrNull() ?: return "—" to ""
        val used = format(latest, digits)
        return if (total.isNullOrBlank()) used to unit else "$used/$total" to unit
    }

    /**
     * The chart's points in a 0–100 box, oldest first.
     *
     * It is a texture, not a measurement — the number above it is the measurement —
     * so it is scaled to its own window, with a floor on the span tied to the
     * metric's ceiling: three tenths of a GB across a tall cell would be a lie, and
     * a window that barely moved would otherwise look like a cliff. A single sample
     * is a level line rather than a dot, and no samples are no line at all.
     */
    fun points(values: List<Double>, ceiling: Double): List<Pair<Double, Double>> {
        if (values.isEmpty()) return emptyList()
        if (values.size == 1) {
            // A window of one has no shape: it is a level line down the middle of
            // the range it could have moved in.
            val level = (FLOOR + TOP) / 2
            return listOf(SIDE to level, 100.0 - SIDE to level)
        }
        val lowValue = values.reduce { acc, value -> minOf(acc, value) }
        val highValue = values.reduce { acc, value -> maxOf(acc, value) }
        val span = max(highValue - lowValue, ceiling * LEAST_SPAN)
        val low = (lowValue + highValue) / 2 - span / 2
        return values.mapIndexed { index, value ->
            val x = SIDE + index.toDouble() / (values.size - 1) * (100 - SIDE * 2)
            val ratio = ((value - low) / span).coerceIn(0.0, 1.0)
            x to (FLOOR - ratio * (FLOOR - TOP))
        }
    }

    /** A number at `digits` decimals. Locale-free: a console prints dots. */
    fun format(value: Double, digits: Int): String {
        if (digits <= 0) return Math.round(value).toString()
        val scale = 10.0.pow(digits)
        val rounded = Math.round(value * scale) / scale
        return String.format(Locale.ROOT, "%.${digits}f", rounded)
    }

    /**
     * How many decimals a `used/total` reading can afford.
     *
     * Three digits of capacity plus a decimal and a slash is wider than a third of
     * a phone card: `40.2/256 GB` was cut off mid-number, and a truncated number is
     * worse than a coarser one. A capacity of a hundred or more — a host disk —
     * therefore prints whole gigabytes, and a small one keeps its tenth.
     */
    fun decimalsFor(total: String?): Int {
        val digits = total?.count { it.isDigit() } ?: 0
        return if (digits >= 3) 0 else 1
    }

    /**
     * Whether a unit can sit after the number it belongs to.
     *
     * A card is a third of a phone wide, and a number that fills it has no room
     * left: `40/256 GB` was printed as `40/256 G`, which is a different unit. When
     * the row is this wide the unit moves up beside the name instead — `DISK GB`
     * with `40/256` under it — because a unit that is wrong or missing is worse
     * than one that is somewhere else on the same card.
     */
    fun unitFits(value: String, unit: String): Boolean {
        if (unit.isEmpty()) return true
        return value.length + 1 + unit.length <= 8
    }

    /** Gigabytes from bytes, the unit the console reads memory and disk in. */
    fun toGb(bytes: Long?): Double? = bytes?.let { it / 1024.0 / 1024.0 / 1024.0 }

    /** A capacity, said the way the card's total is said: whole gigabytes. */
    fun wholeGb(bytes: Long?): String? = toGb(bytes)?.let { format(it, 0) }

    /**
     * The cores a CPU share is drawn against, as the card says it: `4`, or `1.5`
     * for a fractional quota, with no trailing `.0`.
     *
     * The engine reports the quota it lives under, so this is the size of the
     * machine the card is describing rather than the size of the host it sits on.
     */
    fun cores(count: Double?): String? {
        val value = count ?: return null
        if (value <= 0.0) return null
        return if (value == Math.floor(value)) format(value, 0) else format(value, 1)
    }
}
