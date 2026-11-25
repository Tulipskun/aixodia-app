package com.tulipskun.aixodia

import org.json.JSONObject

/**
 * One phone, as the Droidime list knows it.
 *
 * A device is not an engine: it runs no daemon of ours, so it has no shells, no
 * desktop and no `/healthz` of the shape an engine answers with. What the
 * registry can honestly say about it is its own name, the phrase beside it, and
 * whether it has been in touch — [battery] is null until something on that
 * machine says otherwise, and a card draws a dash for it rather than a number
 * nobody measured.
 */
data class DeviceEntry(
    val id: String,
    val name: String,
    val alias: String = "",
    val subtitle: String = "",
    val battery: Int? = null,
    val online: Boolean = false,
) {
    /** What a card draws: the name a person chose, else the device's own. */
    val displayName: String get() = alias.ifBlank { name.ifBlank { id } }
}

/**
 * The device list, read without a network or a Context.
 *
 * `GET /api/devices` is a different route on the same gateway as the engine
 * list, because a device is a different thing: the two lists are two tables, and
 * nothing in this file can produce an engine.
 */
object DeviceCatalog {
    /** The devices in the gateway's JSON, skipping anything unusable. */
    fun parse(json: String): List<DeviceEntry> {
        if (json.isBlank()) return emptyList()
        val body = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val devices = body.optJSONArray("devices") ?: return emptyList()
        val out = ArrayList<DeviceEntry>(devices.length())
        for (index in 0 until devices.length()) {
            val item = devices.optJSONObject(index) ?: continue
            val id = item.text("id")
            if (id.isEmpty()) continue
            out += DeviceEntry(
                id = id,
                name = item.text("name").ifEmpty { id },
                alias = item.text("alias"),
                subtitle = item.text("subtitle"),
                // A null battery is no measurement, not a zero: the difference is
                // a dash and a "0 %" on the card.
                battery = if (item.has("battery") && !item.isNull("battery")) item.optInt("battery") else null,
                online = item.optBoolean("online", false),
            )
        }
        return out
    }

    /** One string field, with a JSON null read as nothing. */
    private fun JSONObject.text(name: String): String =
        if (!has(name) || isNull(name)) "" else optString(name).trim()
}
