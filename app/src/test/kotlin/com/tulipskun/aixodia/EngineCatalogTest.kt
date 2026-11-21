package com.tulipskun.aixodia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The catalog is the only place that decides which engine an address belongs to,
 * so both halves of a switch — the name in the path and the host the list lives
 * on — are answered from one string.
 */
class EngineCatalogTest {

    private val body = """
        {"engines":[
          {"id":"m-8a7d1192","name":"Primary Engine",
           "url":"https://engine.aixodia.dpdns.org/m-8a7d1192",
           "origin_host":"m-8a7d1192.aixodia.dpdns.org",
           "subtitle":"Cloudflare Named Tunnel","tag":"PRIMARY",
           "online":true,"version":"v1.177"},
          {"id":"work-1","name":"Work","url":"https://engine.aixodia.dpdns.org/work-1/",
           "subtitle":"","tag":"","online":false,"version":""}
        ]}
    """.trimIndent()

    @Test
    fun `the list is read with its liveness and version`() {
        val engines = EngineCatalog.parse(body)
        assertEquals(listOf("m-8a7d1192", "work-1"), engines.map { it.id })
        assertEquals("Primary Engine", engines[0].name)
        assertEquals(true, engines[0].online)
        assertEquals("v1.177", engines[0].version)
        assertEquals(false, engines[1].online)
        // A trailing slash is not part of the address.
        assertEquals("https://engine.aixodia.dpdns.org/work-1", engines[1].url)
    }

    @Test
    fun `a field the gateway left null is nothing, not the word null`() {
        // The live registry answers exactly like this: `"tag":null`,
        // `"subtitle":null`. `optString` would have made both the four letters
        // "null", which a card then captioned as if it were a tag.
        val engines = EngineCatalog.parse(
            """{"engines":[{"id":"m-8a7d1192","name":"m-8a7d1192",
                "url":"https://engine.aixodia.dpdns.org/m-8a7d1192",
                "origin_host":"m-8a7d1192.aixodia.dpdns.org","subtitle":null,"tag":null,
                "online":true,"version":"v1.177"}]}""",
        )
        assertEquals("", engines[0].tag)
        assertEquals("", engines[0].subtitle)
        assertEquals("v1.177", engines[0].version)
    }

    @Test
    fun `an entry without a name or an address is dropped`() {
        val engines = EngineCatalog.parse(
            """{"engines":[{"id":"","url":"https://a/x"},{"id":"b","url":"not a url"},{"id":"c","url":"https://a/c"}]}""",
        )
        assertEquals(listOf("c"), engines.map { it.id })
    }

    @Test
    fun `garbage is an empty list, not an exception`() {
        assertEquals(emptyList<EngineEntry>(), EngineCatalog.parse(""))
        assertEquals(emptyList<EngineEntry>(), EngineCatalog.parse("<html>nope</html>"))
        assertEquals(emptyList<EngineEntry>(), EngineCatalog.parse("{}"))
    }

    @Test
    fun `the pick names an engine, and the first one answers when it does not`() {
        val engines = EngineCatalog.parse(body)
        assertEquals("work-1", EngineCatalog.select(engines, "work-1")?.id)
        assertEquals("m-8a7d1192", EngineCatalog.select(engines, "gone")?.id)
        assertEquals("m-8a7d1192", EngineCatalog.select(engines, "")?.id)
        assertNull(EngineCatalog.select(emptyList(), "anything"))
    }

    @Test
    fun `the address of an engine is its name in the path`() {
        assertEquals(
            "https://engine.aixodia.dpdns.org/work-1",
            EngineCatalog.selectedUrl(body, "work-1"),
        )
        assertEquals(
            "https://engine.aixodia.dpdns.org/m-8a7d1192",
            EngineCatalog.selectedUrl(body, ""),
        )
        assertNull(EngineCatalog.selectedUrl("", ""))
    }

    @Test
    fun `an address names the engine in its first path segment`() {
        assertEquals(
            "m-8a7d1192",
            EngineCatalog.engineIdOf("https://engine.aixodia.dpdns.org/m-8a7d1192"),
        )
        assertEquals(
            "m-8a7d1192",
            EngineCatalog.engineIdOf("https://engine.aixodia.dpdns.org/m-8a7d1192/"),
        )
        assertEquals("work-1", EngineCatalog.engineIdOf("https://engine.aixodia.dpdns.org/work-1/api?s=1"))
        // The bare host is the first engine: what a phone is given before it has
        // ever asked for a list.
        assertEquals("", EngineCatalog.engineIdOf("https://engine.aixodia.dpdns.org"))
        assertEquals("", EngineCatalog.engineIdOf("https://engine.aixodia.dpdns.org/"))
        assertEquals("", EngineCatalog.engineIdOf("nonsense"))
    }

    @Test
    fun `the list lives on the host, without the engine name`() {
        assertEquals(
            "https://engine.aixodia.dpdns.org",
            EngineCatalog.registryUrl("https://engine.aixodia.dpdns.org/m-8a7d1192"),
        )
        assertEquals(
            "https://engine.aixodia.dpdns.org",
            EngineCatalog.registryUrl("https://engine.aixodia.dpdns.org"),
        )
        assertEquals(
            "http://127.0.0.1:8787",
            EngineCatalog.registryUrl("http://127.0.0.1:8787/work-1/api"),
        )
        assertNull(EngineCatalog.registryUrl("not a url"))
    }
}
