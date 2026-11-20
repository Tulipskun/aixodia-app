package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.EngineCatalog
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Which engines exist, and which one this phone is on.
 *
 * The list comes from the gateway's `GET /api/engines`, on the host part of the
 * address the phone already has — the engine's own name is a path, so the same
 * host serves the list and every engine behind it. A list that cannot be read is
 * not a failure: the phone keeps the one it stored, because a switcher that
 * empties itself when the network is down is worse than a stale list.
 */
class EngineDirectory(
    private val settings: SettingsStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .dns(ReachableDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build(),
) {
    /** The engines as last stored, without asking anyone. */
    suspend fun catalog(): List<EngineEntry> =
        EngineCatalog.parse(settings.engineSnapshot().json)

    /** The engine the pick names, or the first one listed. */
    suspend fun active(): EngineEntry? {
        val snapshot = settings.engineSnapshot()
        return EngineCatalog.select(EngineCatalog.parse(snapshot.json), snapshot.activeId)
    }

    /** What to show: the stored list, and its age in seconds. */
    suspend fun snapshot(): Snapshot {
        val stored = settings.engineSnapshot()
        return Snapshot(
            engines = EngineCatalog.parse(stored.json),
            activeId = stored.activeId,
            fetchedAt = stored.fetchedAt,
        )
    }

    /**
     * Reads the gateway's list and stores it. The stored list is returned when
     * the gateway cannot be reached, so the caller always has something to show.
     */
    suspend fun refresh(): Snapshot = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl)
        val fetched = base?.let { read("$it/api/engines") }
        if (fetched != null) settings.saveEngines(fetched)
        snapshot()
    }

    /**
     * Moves the phone to one engine: the selection and the address it names are
     * written together, so no reader ever sees a new id with the old address.
     */
    suspend fun select(id: String): EngineEntry? {
        val entry = catalog().firstOrNull { it.id == id.trim() } ?: return null
        settings.selectEngine(entry.id, entry.url)
        return entry
    }

    private fun read(url: String): String? {
        val request = Request.Builder()
            .url(url)
            // The registry answers without a token: it is the one thing a phone
            // must be able to ask before it has signed in anywhere.
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (EngineCatalog.parse(body).isEmpty()) null else body
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Asks one address whether it is an engine, without changing the current
     * one. A switch reads this before writing anything: an engine that does not
     * answer /healthz must not become the phone's new address.
     */
    suspend fun probe(url: String): Probe = withContext(Dispatchers.IO) {
        val base = url.trim().trimEnd('/')
        if (!(base.startsWith("https://") || base.startsWith("http://"))) {
            return@withContext Probe(false, "", "not an http(s) address")
        }
        val request = Request.Builder().url("$base/healthz")
            .header("Accept", "application/json")
            .get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Probe(false, "", "HTTP ${response.code}")
                }
                val json = runCatching { org.json.JSONObject(body) }.getOrNull()
                    ?: return@withContext Probe(false, "", "not an engine")
                // A dashboard host also answers /healthz with 200 — but with a
                // page, not the engine's `ok` field. Require the field so a
                // wrong address can never be mistaken for a healthy engine.
                if (!json.has("ok")) return@withContext Probe(false, "", "not an engine")
                Probe(
                    ok = json.optBoolean("ok", false),
                    version = json.optString("version"),
                    error = "",
                )
            }
        } catch (e: Exception) {
            Probe(false, "", e.message ?: "unreachable")
        }
    }

    data class Probe(val ok: Boolean, val version: String, val error: String)

    data class Snapshot(
        val engines: List<EngineEntry>,
        val activeId: String,
        val fetchedAt: Long,
    ) {
        val active: EngineEntry? get() = EngineCatalog.select(engines, activeId)
    }
}