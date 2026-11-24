package com.tulipskun.aixodia.data.remote

import org.json.JSONObject

/**
 * What the gateway said when the phone asked it to forget an engine.
 *
 * Three answers, and the difference between them is the difference between a
 * result and a guess:
 *
 * * [Deleted] — the row is gone. Every client reads the same table, so the
 *   engine is gone from the console's switcher and from every phone at once.
 *   [urlRemoved] says whether the machine's public address went with it: the
 *   hostname, the tunnel and the keys that made it reachable. A delete that
 *   leaves part of an address standing is still a delete, and the parts that
 *   stood are named rather than hidden.
 * * [Refused] — the gateway would not, and said why. Two of those matter: a live
 *   lease (the engine would rewrite its own row within a beat), and a machine
 *   that has only been quiet for a while — its address is kept, because a
 *   sleeping machine is not a gone one.
 * * [Failed] — nothing was answered, or the answer was not this shape. Never
 *   read as a success: a delete that did not happen must not look like one.
 */
sealed interface EngineDeleteAnswer {
    data class Deleted(
        val id: String,
        val urlRemoved: Boolean = false,
        /**
         * What happened to each part of the address, as the gateway reports it:
         * `dns`, `tunnel`, `keys` → `removed 1` / `none` / `failed: why`.
         */
        val steps: Map<String, String> = emptyMap(),
    ) : EngineDeleteAnswer

    data class Refused(val reason: String, val online: Boolean = false) : EngineDeleteAnswer
    data class Failed(val reason: String) : EngineDeleteAnswer

    /** One line under the tiles. */
    fun headline(): String = when (this) {
        is Deleted -> when {
            urlRemoved -> "Deleted $id, and its address"
            // Everything that did not finish is named: a half teardown is a state
            // somebody has to act on, not a footnote.
            steps.values.any { it.startsWith("failed") } ->
                "Deleted $id, address partly standing: " + steps.entries
                    .filter { it.value.startsWith("failed") }
                    .joinToString("; ") { "${it.key} ${it.value.removePrefix("failed: ")}" }
            else -> "Deleted $id"
        }
        is Refused -> reason
        is Failed -> "Cannot delete: $reason"
    }

    companion object {
        fun parse(body: String, status: Int): EngineDeleteAnswer {
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return Failed(if (body.isBlank()) "HTTP $status with no body" else body.take(120))
            if (obj.optBoolean("ok", false)) {
                val id = obj.optString("deleted")
                if (id.isBlank()) return Failed("the gateway confirmed nothing")
                val steps = obj.optJSONObject("steps")?.let { json ->
                    json.keys().asSequence().associateWith { key -> json.optString(key) }
                }.orEmpty()
                return Deleted(id, urlRemoved = obj.optBoolean("url_removed", false), steps = steps)
            }
            val reason = obj.optString("error").ifBlank { "the gateway refused (HTTP $status)" }
            val code = obj.optString("code")
            return Refused(reason, online = code == "ENGINE_ONLINE")
        }
    }
}

/**
 * What the gateway said when the phone gave an engine a name.
 *
 * [Saved] carries the alias as the gateway stored it — trimmed and bounded — so
 * the card draws the same string the next read will bring back.
 */
sealed interface EngineRenameAnswer {
    data class Saved(val id: String, val alias: String) : EngineRenameAnswer
    data class Failed(val reason: String) : EngineRenameAnswer

    fun headline(): String = when (this) {
        is Saved -> if (alias.isBlank()) "Name cleared" else "Named $alias"
        is Failed -> "Cannot rename: $reason"
    }

    companion object {
        fun parse(body: String, status: Int): EngineRenameAnswer {
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return Failed(if (body.isBlank()) "HTTP $status with no body" else body.take(120))
            if (!obj.optBoolean("ok", false)) {
                return Failed(obj.optString("error").ifBlank { "the gateway refused (HTTP $status)" })
            }
            val id = obj.optString("id")
            if (id.isBlank()) return Failed("the gateway named nothing")
            return Saved(id, if (obj.has("alias") && !obj.isNull("alias")) obj.optString("alias") else "")
        }
    }
}
