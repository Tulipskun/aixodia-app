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
 * * [Refused] — the gateway would not, and said why. The one that matters is a
 *   live lease: an engine still beating rewrites its own row every twenty
 *   seconds, so a delete it allowed would be undone while the reader was told it
 *   had worked.
 * * [Failed] — nothing was answered, or the answer was not this shape. Never
 *   read as a success: a delete that did not happen must not look like one.
 */
sealed interface EngineDeleteAnswer {
    data class Deleted(val id: String) : EngineDeleteAnswer
    data class Refused(val reason: String, val online: Boolean = false) : EngineDeleteAnswer
    data class Failed(val reason: String) : EngineDeleteAnswer

    /** One line under the tiles. */
    fun headline(): String = when (this) {
        is Deleted -> "Deleted $id"
        is Refused -> reason
        is Failed -> "Cannot delete: $reason"
    }

    companion object {
        fun parse(body: String, status: Int): EngineDeleteAnswer {
            val obj = runCatching { JSONObject(body) }.getOrNull()
                ?: return Failed(if (body.isBlank()) "HTTP $status with no body" else body.take(120))
            if (obj.optBoolean("ok", false)) {
                val id = obj.optString("deleted")
                return if (id.isBlank()) Failed("the gateway confirmed nothing") else Deleted(id)
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
