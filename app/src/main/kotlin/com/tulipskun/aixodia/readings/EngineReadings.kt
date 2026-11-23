package com.tulipskun.aixodia.readings

import com.tulipskun.aixodia.data.remote.Health
import org.json.JSONArray
import org.json.JSONObject

/** How many readings a card keeps: the window a chart is drawn from. */
const val READINGS_WINDOW = 60

/**
 * One engine's last few readings, newest last — the console's three metrics, kept
 * in the units it says them in: CPU as a share of the machine, memory and disk as
 * the gigabytes in use, because that is what the card prints next to the total.
 *
 * It is a picture, not a measurement: the number on the card is the newest sample
 * and nothing else, so a short window, one sample or none at all are all normal
 * states and none of them is an error.
 */
data class EngineReadings(
    val cpu: List<Double> = emptyList(),
    val ram: List<Double> = emptyList(),
    val disk: List<Double> = emptyList(),
    /** The cores this engine's CPU share is drawn against, as the card says it. */
    val cpuCores: String? = null,
    val ramTotalGb: String? = null,
    val diskTotalGb: String? = null,
) {
    private fun List<Double>.plus(value: Double?): List<Double> =
        if (value == null) this else (this + value).takeLast(READINGS_WINDOW)

    fun with(health: Health): EngineReadings =
        copy(
            cpu = cpu.plus(health.cpuPercent),
            ram = ram.plus(MetricShape.toGb(health.memoryUsedBytes)),
            disk = disk.plus(MetricShape.toGb(health.diskUsedBytes)),
            // A capacity is a fact about the machine, not a sample: it stays where
            // it was when a reply leaves it out rather than flickering to nothing.
            cpuCores = MetricShape.cores(health.cpuCores) ?: cpuCores,
            ramTotalGb = MetricShape.wholeGb(health.memoryTotalBytes) ?: ramTotalGb,
            diskTotalGb = MetricShape.wholeGb(health.diskTotalBytes) ?: diskTotalGb,
        )

    companion object {
        /**
         * The whole set of cards' windows, as one JSON object keyed by engine id.
         *
         * This is what survives a process the system killed while it was away —
         * the reason a graph does not start over every time the app is reopened.
         */
        fun encodeAll(byEngine: Map<String, EngineReadings>): String {
            val root = JSONObject()
            for ((id, readings) in byEngine) {
                if (id.isBlank()) continue
                root.put(id, JSONObject().apply {
                    put("cpu", readings.cpu.toJson())
                    put("ram", readings.ram.toJson())
                    put("disk", readings.disk.toJson())
                    readings.cpuCores?.let { put("cpuCores", it) }
                    readings.ramTotalGb?.let { put("ramTotal", it) }
                    readings.diskTotalGb?.let { put("diskTotal", it) }
                })
            }
            return root.toString()
        }

        /** Reads back what [encodeAll] wrote; anything unreadable is an empty card. */
        fun decodeAll(json: String): Map<String, EngineReadings> {
            val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
            val out = mutableMapOf<String, EngineReadings>()
            for (id in root.keys()) {
                val entry = root.optJSONObject(id) ?: continue
                out[id] = EngineReadings(
                    cpu = entry.samples("cpu"),
                    ram = entry.samples("ram"),
                    disk = entry.samples("disk"),
                    cpuCores = entry.optString("cpuCores").takeIf { it.isNotBlank() },
                    ramTotalGb = entry.optString("ramTotal").takeIf { it.isNotBlank() },
                    diskTotalGb = entry.optString("diskTotal").takeIf { it.isNotBlank() },
                )
            }
            return out
        }

        private fun List<Double>.toJson(): JSONArray = JSONArray().apply { forEach { put(it) } }

        private fun JSONObject.samples(name: String): List<Double> {
            val array = optJSONArray(name) ?: return emptyList()
            val out = ArrayList<Double>(array.length())
            for (index in 0 until array.length()) {
                val value = array.optDouble(index, Double.NaN)
                if (!value.isNaN()) out += value
            }
            return out.takeLast(READINGS_WINDOW)
        }
    }
}
