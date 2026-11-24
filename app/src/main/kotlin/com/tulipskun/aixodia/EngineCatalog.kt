package com.tulipskun.aixodia

import org.json.JSONObject

/**
 * One engine as the gateway lists it.
 *
 * `id` is the path the engine answers under (`https://<host>/<id>/…`) and `url`
 * is the address to call. `online` is the gateway's own answer — "this engine
 * replied within the last minute" — so it is a hint, not a promise.
 * [alias] is the name a person gave the machine in the app. It is a caption and
 * nothing else: [id] is what every address, ticket and revocation uses, and
 * renaming never moves it. An empty alias means nobody has named this machine
 * yet, and [name] — the machine's own name — is what is drawn.
 */
data class EngineEntry(
    val id: String,
    val name: String,
    val url: String,
    val subtitle: String = "",
    val tag: String = "",
    val online: Boolean = false,
    val version: String = "",
    val alias: String = "",
) {
    /** What a card draws: the name a person chose, else the machine's own. */
    val displayName: String get() = alias.ifBlank { name.ifBlank { id } }
}

/**
 * The engine list, read and picked without a network or a Context.
 *
 * The gateway serves the list at `GET /api/engines` on the host part of whatever
 * address the phone has, and every engine is the first path segment of that
 * address. So the two questions this needs to answer are both about the shape of
 * one string: which engine does an address name, and what is the address of the
 * list for it.
 */
object EngineCatalog {
    /** The engines in the gateway's JSON, skipping anything unusable. */
    fun parse(json: String): List<EngineEntry> {
        if (json.isBlank()) return emptyList()
        val body = try {
            JSONObject(json)
        } catch (_: Exception) {
            return emptyList()
        }
        val engines = body.optJSONArray("engines") ?: return emptyList()
        val out = ArrayList<EngineEntry>(engines.length())
        for (index in 0 until engines.length()) {
            val item = engines.optJSONObject(index) ?: continue
            val id = item.text("id")
            val url = item.text("url").trimEnd('/')
            if (id.isEmpty() || !isAbsolute(url)) continue
            out += EngineEntry(
                id = id,
                name = item.text("name").ifEmpty { id },
                url = url,
                subtitle = item.text("subtitle"),
                tag = item.text("tag"),
                online = item.optBoolean("online", false),
                version = item.text("version"),
                alias = item.text("alias"),
            )
        }
        return out
    }

    /**
     * One string field, with a JSON null read as nothing.
     *
     * `optString` turns a null into the four letters "null", which is how a
     * card whose engine had no tag came to be captioned `null · v1.177`.
     */
    private fun JSONObject.text(name: String): String =
        if (!has(name) || isNull(name)) "" else optString(name).trim()

    /** The engine the pick names, falling back to the first one listed. */
    fun select(engines: List<EngineEntry>, activeId: String): EngineEntry? {
        val wanted = activeId.trim()
        return engines.firstOrNull { it.id == wanted } ?: engines.firstOrNull()
    }

    /** The selected engine's address, or null when there is no list. */
    fun selectedUrl(json: String, activeId: String): String? =
        select(parse(json), activeId)?.url?.takeIf(::isAbsolute)

    /**
     * The engine an address names: its first path segment, or `""` for the bare
     * host — which is the first engine, the one a phone is given before it has
     * asked for a list.
     */
    fun engineIdOf(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        if (!isAbsolute(trimmed)) return ""
        val authorityStart = trimmed.indexOf("//") + 2
        val pathStart = trimmed.indexOf('/', authorityStart)
        if (pathStart < 0) return ""
        return trimmed.substring(pathStart).trim('/').split('/').first()
    }

    /**
     * Where the list lives for an address: the host part, without the engine's
     * own name in it.
     */
    fun registryUrl(daemonUrl: String): String? {
        val trimmed = daemonUrl.trim().trimEnd('/')
        if (!isAbsolute(trimmed)) return null
        val authorityStart = trimmed.indexOf("//") + 2
        val pathStart = trimmed.indexOf('/', authorityStart)
        return if (pathStart < 0) trimmed else trimmed.substring(0, pathStart)
    }

    private fun isAbsolute(url: String): Boolean =
        url.startsWith("https://") || url.startsWith("http://")
}
