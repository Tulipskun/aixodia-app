package com.tulipskun.aixodia.data.remote

import org.json.JSONObject

/**
 * What an engine said when the phone asked it to update itself.
 *
 * The three answers are the three real outcomes, and none of them is a guess:
 *
 * * [Current] — the release page was read and the running bytes are it.
 * * [Queued] — a newer build exists and the engine will swap itself when it is
 *   idle. The swap is an `execve`, so the request is answered *before* it and
 *   the pid never changes; "queued" is the only honest word for it.
 * * [Failed] — the engine could not ask the release page. Nothing was installed,
 *   because installing on an unanswered question would replace a working engine
 *   with whatever came back.
 *
 * `published` is the version's name: GitHub names the asset by architecture,
 * not by build, so the asset's own timestamp is what both sides compare.
 */
sealed interface EngineUpdateAnswer {
    data class Current(val published: String) : EngineUpdateAnswer
    data class Queued(val published: String) : EngineUpdateAnswer
    data class Failed(val reason: String) : EngineUpdateAnswer

    /** One line a reader can act on, and the truth when there is nothing to do. */
    fun headline(): String = when (this) {
        is Current -> "Already the published build" + say(published)
        is Queued -> "Update queued — it swaps when no chat is running" + say(published)
        is Failed -> "Cannot check the release page: $reason"
    }

    private fun say(published: String): String =
        if (published.isBlank()) "" else " · $published"

    companion object {
        /**
         * Reads the engine's reply. A body that is not the shape this expects is
         * a failure with the body's own words in it, never a silent success: an
         * update that did not happen must not read as one that did.
         */
        fun parse(body: String, status: Int = 200): EngineUpdateAnswer {
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return Failed(if (body.isBlank()) "HTTP $status with no body" else body.take(120))
            if (!obj.optBoolean("ok", false)) {
                val reason = obj.optString("error").ifBlank { "the engine refused" }
                return Failed(reason)
            }
            val published = obj.optString("published")
            return if (obj.optBoolean("update", false)) Queued(published) else Current(published)
        }
    }
}
