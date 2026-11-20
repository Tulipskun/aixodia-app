package com.tulipskun.aixodia.data.remote

import com.tulipskun.aixodia.ConnConfig
import com.tulipskun.aixodia.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * A switch must probe the candidate before writing it: a dead engine must not
 * become the phone's address. The probe is the only new network step in the
 * picker, so it is tested here with an in-memory OkHttp interceptor — no real
 * engine and no new test dependency.
 */
class EngineProbeTest {

    /** `current()` is suspend, so the mock setup must run inside a coroutine. */
    private suspend fun settings(): SettingsStore {
        val s: SettingsStore = mock()
        whenever(s.current()).thenReturn(
            ConnConfig("", "https://engine.aixodia.dpdns.org", "", "", "", "s", sessionToken = "A"),
        )
        return s
    }

    /** An interceptor answering /healthz like an engine (or like a dashboard). */
    private fun client(body: String, code: Int = 200): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val path = chain.request().url.encodedPath
            val responseBody = if (path.endsWith("/healthz")) body else "not found"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (path.endsWith("/healthz")) code else 404)
                .message("test")
                .body(responseBody.toResponseBody())
                .build()
        }).build()

    @Test
    fun `a healthy engine is reported with its version`() = runBlocking {
        val directory = EngineDirectory(settings(), client("""{"ok":true,"version":"v1.177"}"""))
        val probe = directory.probe("https://engine.aixodia.dpdns.org/work-1")
        assertTrue(probe.ok)
        assertEquals("v1.177", probe.version)
        assertEquals("", probe.error)
    }

    @Test
    fun `a 200 page without the ok field is not an engine`() = runBlocking {
        // A dashboard host answers /healthz with 200 and HTML; defaulting ok to
        // true would let a wrong address be saved as a healthy engine.
        val directory = EngineDirectory(settings(), client("<!doctype html><title>Dashboard</title>"))
        val probe = directory.probe("https://dashboard.example.test")
        assertFalse(probe.ok)
        assertTrue(probe.error.contains("not an engine"))
    }

    @Test
    fun `an engine that answers ok false is not switched to`() = runBlocking {
        val directory = EngineDirectory(settings(), client("""{"ok":false}"""))
        val probe = directory.probe("https://engine.aixodia.dpdns.org/down")
        assertFalse(probe.ok)
    }

    @Test
    fun `a non-healthz error is reported`() = runBlocking {
        val directory = EngineDirectory(settings(), client("", code = 503))
        val probe = directory.probe("https://engine.aixodia.dpdns.org/busy")
        assertFalse(probe.ok)
        assertTrue(probe.error.contains("503"))
    }

    @Test
    fun `a relative address is refused before any dial`() = runBlocking {
        val directory = EngineDirectory(settings(), client("""{"ok":true}"""))
        val probe = directory.probe("/engine")
        assertFalse(probe.ok)
        assertTrue(probe.error.contains("http(s)"))
    }
}
