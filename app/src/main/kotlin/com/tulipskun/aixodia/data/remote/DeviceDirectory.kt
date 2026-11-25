package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.DeviceCatalog
import com.tulipskun.aixodia.DeviceEntry
import com.tulipskun.aixodia.EngineCatalog
import com.tulipskun.aixodia.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        } catch (e: Exception) {
            DeviceRead.Failed(e.message ?: "no reply")
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

    /** Two answers, because an empty list is only one of them. */
    sealed interface DeviceRead {
        data class Listing(val devices: List<DeviceEntry>) : DeviceRead
        data class Failed(val reason: String) : DeviceRead
    }
}
