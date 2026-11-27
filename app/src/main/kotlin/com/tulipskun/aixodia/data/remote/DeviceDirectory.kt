package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.DeviceCatalog
import com.tulipskun.aixodia.DeviceEntry
import com.tulipskun.aixodia.EngineCatalog
import com.tulipskun.aixodia.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The phones DroidIME runs on, which are not engines and are not listed with them.
 *
 * The list is `GET /api/devices` on the same host the engine list comes from —
 * one gateway, two namespaces — and the only thing asked of a device itself is
 * whether it answers. It answers through the gateway, which attaches the Access
 * service token, so a phone never holds a credential for somebody's device.
 */
class DeviceDirectory(
    private val settings: SettingsStore,
    private val client: OkHttpClient = EngineDirectory.client(),
) {
    /**
     * Reads the list. A gateway that cannot be reached is said out loud rather
     * than drawn as an empty shelf: "no devices" and "I could not ask" are two
     * different things, and only one of them is a fact about the person's phones.
     */
    suspend fun devices(): DeviceRead = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl)
            ?: return@withContext DeviceRead.Failed("this phone has no gateway address")
        val request = Request.Builder().url("$base/api/devices")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DeviceRead.Failed("HTTP ${response.code}")
                }
                DeviceRead.Listing(DeviceCatalog.parse(response.body?.string().orEmpty()))
            }
        } catch (e: CancellationException) {
            // The Droidime list was closed; that is not a device being silent.
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Devices", "listing: $e")
            DeviceRead.Failed("couldn't reach the gateway")
        }
    }

    /** Whether one device answers at all. Its own `/healthz`, through the gateway. */
    suspend fun health(id: String): Boolean = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl) ?: return@withContext false
        val request = Request.Builder().url("$base/api/devices/$id/healthz")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * The device's screen, drawn by its own MCP server. PNG bytes, or null when
     * the phone did not answer. The picture is taken by the gateway, which holds
     * the Access service token, so this app never keeps a key to somebody's phone.
     */
    suspend fun screen(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl) ?: return@withContext null
        val request = Request.Builder().url("$base/api/devices/$id/screen")
            .header("Accept", "image/png")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.bytes()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Devices", "screen $id: $e")
            null
        }
    }

    /** The device's own idea of its screen size, in the pixels a tap speaks. */
    suspend fun screenSize(id: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl) ?: return@withContext null
        val body = JSONObject().put("name", "screen_size").put("arguments", JSONObject()).toString()
        val request = Request.Builder().url("$base/api/devices/$id/tool")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                DeviceTools.size(response.body?.string().orEmpty())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Devices", "screen size $id: $e")
            null
        }
    }

    /** One touch on a device, in its own pixels. True when the phone took it. */
    suspend fun tap(id: String, x: Int, y: Int): Boolean = withContext(Dispatchers.IO) {
        val base = EngineCatalog.registryUrl(settings.current().daemonUrl) ?: return@withContext false
        val body = JSONObject().put("name", "tap")
            .put("arguments", JSONObject().put("x", x).put("y", y))
            .toString()
        val request = Request.Builder().url("$base/api/devices/$id/tool")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("Devices", "tap $id: $e")
            false
        }
    }

    /** Two answers, because an empty list is only one of them. */
    sealed interface DeviceRead {
        data class Listing(val devices: List<DeviceEntry>) : DeviceRead
        data class Failed(val reason: String) : DeviceRead
    }
}
