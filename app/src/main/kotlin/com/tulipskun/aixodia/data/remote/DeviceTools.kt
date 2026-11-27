package com.tulipskun.aixodia.data.remote

import org.json.JSONObject

/**
 * The words a DroidIME device's answers carry that this app has to read.
 *
 * MCP answers a tool call as a list of parts — text and pictures — and the one
 * number this app needs out of that list is the screen size a tap is spoken in.
 * It is pulled out here rather than in the screen, so the reading can be pinned
 * by a test without a phone and without a network.
 */
object DeviceTools {

    /**
     * `width=900 height=1800`, taken from the text part of a `screen_size`
     * answer. Null when the answer carries no such pair: a device that does not
     * say how big it is cannot be tapped accurately, and guessing is worse than
     * not tapping.
     */
    fun size(answer: String): Pair<Int, Int>? {
        val text = text(answer) ?: return null
        val width = number(text, "width") ?: return null
        val height = number(text, "height") ?: return null
        if (width <= 0 || height <= 0) return null
        return width to height
    }

    /** The first text part of a tool result, or null when it carries none. */
    fun text(answer: String): String? = try {
        val result = JSONObject(answer).optJSONObject("result") ?: return null
        val parts = result.optJSONArray("content") ?: return null
        (0 until parts.length())
            .mapNotNull { parts.optJSONObject(it) }
            .firstOrNull { it.optString("type") == "text" }
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun number(text: String, name: String): Int? {
        val found = Regex("$name\\s*=\\s*(\\d+)").find(text) ?: return null
        return found.groupValues[1].toIntOrNull()
    }
}
