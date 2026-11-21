package com.tulipskun.aixodia.data.remote

import org.json.JSONObject

/**
 * What one engine's `/healthz` says about the machine it runs on.
 *
 * The endpoint is the same one the switcher probes and the gateway polls, so the
 * readings cost the phone nothing extra: they arrive in the reply that already
 * says whether the engine answers at all. Every field is optional because they
 * are read from the machine's own files — an engine on a host without `/proc`
 * reports nothing rather than inventing a number, and a card that has no reading
 * draws the console's em dash instead.
 */
data class Health(
    val cpuPercent: Double? = null,
    val memoryPercent: Double? = null,
    val memoryUsedBytes: Long? = null,
    val memoryTotalBytes: Long? = null,
    val diskPercent: Double? = null,
    val uptimeSeconds: Double? = null,
) {
    /** Nothing worth drawing: the reply was an engine's, but its host said nothing. */
    val isEmpty: Boolean
        get() = cpuPercent == null && memoryPercent == null && diskPercent == null

    companion object {
        /**
         * Reads a `/healthz` body, or null when it is not an engine's answer.
         *
         * A dashboard host answers that path with 200 and a page as well, and a
         * wrong address must never look like a machine with no load, so the
         * engine's `ok` field is required here exactly as it is in the probe.
         */
        fun parse(json: String): Health? {
            val body = runCatching { JSONObject(json) }.getOrNull() ?: return null
            if (!body.has("ok")) return null
            return Health(
                cpuPercent = body.percent("cpu_percent"),
                memoryPercent = body.percent("memory_percent"),
                memoryUsedBytes = body.bytes("memory_used_bytes"),
                memoryTotalBytes = body.bytes("memory_total_bytes"),
                diskPercent = body.percent("disk_percent"),
                uptimeSeconds = body.number("uptime_seconds"),
            )
        }

        private fun JSONObject.number(name: String): Double? {
            if (!has(name) || isNull(name)) return null
            val value = optDouble(name, Double.NaN)
            return if (value.isNaN()) null else value
        }

        /** A percentage, clamped to the range a bar can draw. */
        private fun JSONObject.percent(name: String): Double? =
            number(name)?.coerceIn(0.0, 100.0)

        private fun JSONObject.bytes(name: String): Long? {
            if (!has(name) || isNull(name)) return null
            val value = optLong(name, -1L)
            return if (value < 0L) null else value
        }
    }
}
