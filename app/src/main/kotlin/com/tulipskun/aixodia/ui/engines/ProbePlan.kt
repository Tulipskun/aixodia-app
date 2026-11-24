package com.tulipskun.aixodia.ui.engines

/**
 * Which cards to ask this tick.
 *
 * A card's word — online or offline — is what the phone last measured, not what
 * the registry said when the list was fetched. The lease in the registry is up
 * to a minute old the moment it is read and does not move afterwards, so a
 * machine that died a second after the fetch was drawn as up for as long as the
 * screen stayed open, and a machine that started afterwards was drawn as down
 * and, worse, never asked: the old loop skipped everything the registry called
 * offline. That is the whole of "sometimes offline but shows online, and the
 * other way round": two sources of truth, one of them frozen.
 *
 * So everything is asked, on a schedule that keeps a live engine's numbers fresh
 * (every tick) and does not spend a dead engine's timeout on every tick. The
 * arithmetic lives here, away from Compose, because it is the kind a thumb
 * triggers and a wrong answer is a lying light.
 */
internal class ProbePlan(
    /** Ticks to wait before asking an engine that did not answer again. */
    private val retryTicks: Int = 5,
) {
    private val askedAt = mutableMapOf<String, Int>()

    /** True when this engine is due to be asked on [tick]. */
    fun due(id: String, online: Boolean?, tick: Int): Boolean {
        // A card that is up is asked every tick: its readings are the graph, and
        // a graph with a hole between samples is a line drawn across a death.
        if (online == true) {
            askedAt[id] = tick
            return true
        }
        val last = askedAt[id]
        if (last == null || tick - last >= retryTicks) {
            askedAt[id] = tick
            return true
        }
        return false
    }

    /** Engines that have left the list are not asked again, and not remembered. */
    fun forget(known: Collection<String>) {
        askedAt.keys.retainAll(known.toSet())
    }
}
