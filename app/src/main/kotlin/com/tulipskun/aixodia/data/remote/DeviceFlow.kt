package com.tulipskun.aixodia.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * The device authorization flow from `auth_system.md`.
 *
 * The app never types an email and never sees a password: the auth Worker is
 * the sign-in authority, and the browser does the proving.
 *
 * 1. [requestCode] asks the Worker for a short code. The Worker mints it against
 *    the allowlist's authority, not against this device.
 * 2. The app opens [DeviceCode.verificationUrl] (optionally prefilling the code)
 *    where Cloudflare Access runs the login and the page offers Approve.
 * 3. [poll] asks until the Worker hands over a bearer token, then stops. The
 *    token is delivered exactly once, so a second poll answers `delivered` and
 *    the app must keep what it was given.
 *
 * Nothing here talks to the engine: the engine may be down while the user signs
 * in, and the Worker is what holds the session. Once a token exists the engine
 * accepts it after its next sync.
 */
object DeviceFlow {
    private const val CODE_PATH = "/auth/device/code"
    private const val TOKEN_PATH = "/auth/device/token"

    private val client: OkHttpClient by lazy { OkHttpClient() }
    private val jsonType = "application/json".toMediaType()

    /** The code to show the user and the address to open. */
    data class DeviceCode(
        /** Opaque secret polled for; never shown to the user. */
        val deviceCode: String,
        /** The human-typed form, e.g. `WXYZ-2041`. */
        val userCode: String,
        /** Where the user approves; opened in the browser. */
        val verificationUrl: String,
        /** Allowed seconds between polls. The Worker answers `slow_down` if
         *  pressed harder, so this is honoured rather than ignored. */
        val interval: Int,
        /** Seconds before the code itself dies. */
        val expiresIn: Int,
    ) {
        /** The page with the code already filled in, so the user only approves. */
        val openUrl: String
            get() = verificationUrl + "?code=" + java.net.URLEncoder.encode(deviceCode, "UTF-8")
    }

    /** One poll's answer. */
    sealed interface Poll {
        /** The token. Store it, then stop polling. */
        data class Active(val token: String) : Poll
        /** Not approved yet; poll again after [interval]. */
        data object Pending : Poll
        /** The Worker wants less polling: wait at least this long. */
        data object SlowDown : Poll
        /** The code was already redeemed; the app lost the token. */
        data object Delivered : Poll
        /** The code expired or was denied. Start again. */
        data class Failed(val reason: String) : Poll
    }

    /** A refusal worth showing, with the Worker's code. */
    class Refused(val code: String, message: String) : Exception(message)

    /**
     * Ask the Worker for a code. Throws [Refused] on a refusal so the UI can show
     * the Worker's wording, which is about the account, not the network.
     */
    suspend fun requestCode(origin: String, deviceName: String): DeviceCode =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("device_name", deviceName).toString()
            val request = Request
                .Builder()
                .url(origin.trimEnd('/') + CODE_PATH)
                .post(body.toRequestBody(jsonType))
                .build()
            // Asking for a code is the one step the user watches: a lookup that
            // misses once (a VPN whose interface has no resolver, a network
            // mid-switch) is retried rather than reported as a refusal, which
            // would say the account is wrong when the network was.
            val answer = retrying {
                val response = client.newCall(request).execute()
                response.use { it.code to it.body?.string().orEmpty() }
            }
            val (status, text) = answer
            if (status !in 200..299) throw refused(status, text)
            val json = JSONObject(text)
            DeviceCode(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUrl = json.getString("verification_url"),
                interval = json.optInt("interval", 5),
                expiresIn = json.optInt("expires_in", 600),
            )
        }

    /**
     * Runs [block], retrying a network-level failure a few times.
     *
     * Only the transport is retried: an HTTP answer, including a refusal, is
     * returned as it came, because asking again cannot change it.
     */
    private suspend fun <T> retrying(attempts: Int = 3, block: () -> T): T {
        var last: IOException? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (network: IOException) {
                last = network
                if (attempt < attempts - 1) delay(1_000L * (attempt + 1))
            }
        }
        throw last ?: IOException("Unable to reach the sign-in service")
    }

    /** One poll. Never throws for the normal waiting states. */
    suspend fun poll(origin: String, deviceCode: String): Poll =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("device_code", deviceCode).toString()
            val request = Request
                .Builder()
                .url(origin.trimEnd('/') + TOKEN_PATH)
                .post(body.toRequestBody(jsonType))
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (network: IOException) {
                // A poll is a question that can be asked again; a DNS answer
                // that is missing this second (a VPN without a resolver, a
                // network that just changed) is not an answer at all. The code
                // lives for ten minutes, so waiting out a dip costs nothing.
                return@withContext Poll.Pending
            }
            response.use {
                val text = it.body?.string().orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                    ?: return@use Poll.Failed("Unable to read the answer")
                when (json.optString("status")) {
                    "ok" -> Poll.Active(json.getString("access_token"))
                    // RFC 8628 calls this `authorization_pending`; the Worker
                    // answers the short `pending`. The longer name was read
                    // here and nowhere else, so the first poll of every
                    // sign-in fell through to Failed and the browser's
                    // approval arrived with nothing left to collect it.
                    "pending", "authorization_pending" -> Poll.Pending
                    "slow_down" -> Poll.SlowDown
                    "delivered" -> Poll.Delivered
                    "expired" -> Poll.Failed("The code expired. Start again.")
                    "denied" -> Poll.Failed("The sign-in was denied.")
                    "revoked" -> Poll.Failed("The sign-in was revoked. Start again.")
                    "not_found" -> Poll.Failed("That code is not known. Start again.")
                    else -> Poll.Failed(json.optString("error", "Sign-in failed"))
                }
            }
        }

    /**
     * Poll until the token arrives, the code dies or [giveUp] elapses.
     *
     * The loop is short by design: the user is looking at the browser right now,
     * so a minute of waiting covers the approval, and giving up quietly is better
     * than a spinner that never stops.
     */
    suspend fun awaitToken(
        origin: String,
        code: DeviceCode,
        giveUpSeconds: Int = 180,
        onWaiting: (() -> Unit)? = null,
    ): String {
        var waited = 0
        var interval = code.interval.coerceAtLeast(2)
        while (waited < giveUpSeconds) {
            delay(interval * 1000L)
            waited += interval
            onWaiting?.invoke()
            when (val answer = poll(origin, code.deviceCode)) {
                is Poll.Active -> return answer.token
                Poll.Pending -> Unit
                Poll.SlowDown -> interval += 5
                Poll.Delivered -> throw Refused(
                    "ALREADY_DELIVERED",
                    "The code was already used. Start again.",
                )
                is Poll.Failed -> throw Refused("FAILED", answer.reason)
            }
        }
        throw Refused("TIMEOUT", "Still waiting for approval. Tap Sign in again.")
    }

    private fun refused(status: Int, text: String): Refused {
        val json = runCatching { JSONObject(text) }.getOrNull()
        val code = json?.optString("code").orEmpty().ifBlank { "HTTP_$status" }
        val message = json?.optString("message").orEmpty().ifBlank { "Sign-in is unavailable" }
        return Refused(code, message)
    }
}
