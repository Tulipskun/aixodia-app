package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.ConnConfig
import com.tulipskun.aixodia.SettingsStore

/**
 * Supplies the engine credential for a request.
 *
 * The engine issues the session token now, so this reads it from
 * [SettingsStore] and presents it as a bearer. Kept as a class with the same
 * small surface the transports already call.
 */
class ClientToken(
    private val tokenProvider: (ConnConfig) -> String = { "" },
) {
    suspend fun get(config: ConnConfig, settings: SettingsStore): String {
        val fixed = fixedValue
        if (fixed != null) return fixed
        val token = tokenProvider(config).ifBlank { config.sessionToken }
        require(token.isNotBlank()) { "Sign in to AIxodia to continue" }
        return token
    }

    /** Legacy pass-through overload retained for isolated transport tests. */
    suspend fun get(sessionToken: String, daemonUrl: String): String {
        val fixed = fixedValue
        if (fixed != null) return fixed
        require(sessionToken.isNotBlank()) { "Sign in to AIxodia first" }
        return sessionToken
    }

    /** Sessions are minted by the engine; re-run the browser hand-off instead. */
    suspend fun refresh(refreshToken: String, daemonUrl: String): EngineSession =
        throw IllegalStateException("Sign in again to renew the session")

    fun reset() = Unit

    companion object {
        val shared: ClientToken = ClientToken()
        val passThrough: ClientToken = ClientToken()
        fun fixed(value: String): ClientToken = ClientToken().also { it.fixedValue = value }
    }

    private var fixedValue: String? = null
}

/** Kept for source compatibility with callers that stored engine sessions. */
data class EngineSession(
    val token: String,
    val expiresAt: Long,
    val refreshToken: String,
    val refreshExpiresAt: Long,
    val user: String,
)
