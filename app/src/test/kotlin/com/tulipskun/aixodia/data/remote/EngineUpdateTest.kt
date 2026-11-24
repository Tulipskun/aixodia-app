package com.tulipskun.aixodia.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUpdateTest {

    @Test
    fun `an engine on the published build installs nothing`() {
        val answer = EngineUpdateAnswer.parse(
            """{"ok":true,"update":false,"pending":false,"published":"2026-11-24T00:22:31Z"}""",
        )
        assertTrue(answer is EngineUpdateAnswer.Current)
        assertEquals("Already the published build · 2026-11-24T00:22:31Z", answer.headline())
    }

    @Test
    fun `a queued swap is not called done`() {
        val answer = EngineUpdateAnswer.parse(
            """{"ok":true,"update":true,"pending":true,"published":"2026-11-24T01:00:00Z"}""",
        )
        assertTrue(answer is EngineUpdateAnswer.Queued)
        // The engine answers before it replaces itself, so the honest word is
        // "queued" — and it names the version it will become.
        assertEquals(
            "Update queued — it swaps when no chat is running · 2026-11-24T01:00:00Z",
            answer.headline(),
        )
    }

    @Test
    fun `a release page that did not answer says so and installs nothing`() {
        val answer = EngineUpdateAnswer.parse("""{"ok":false,"error":"github release: 503"}""")
        assertTrue(answer is EngineUpdateAnswer.Failed)
        assertEquals("Cannot check the release page: github release: 503", answer.headline())
    }

    @Test
    fun `a body that is not a reply is a failure, never a quiet success`() {
        for (body in listOf("", "nope", "[]", "{}")) {
            val answer = EngineUpdateAnswer.parse(body)
            assertTrue("for <$body>", answer is EngineUpdateAnswer.Failed)
        }
    }

    @Test
    fun `an empty answer names the status that carried it`() {
        // A 404 and a 502 are different diseases; the reader gets the code.
        val answer = EngineUpdateAnswer.parse("", 404)
        assertEquals("Cannot check the release page: HTTP 404 with no body", answer.headline())
    }

    @Test
    fun `a body that is not the shape expected is quoted back`() {
        val answer = EngineUpdateAnswer.parse("<html>not found</html>", 502)
        assertEquals("Cannot check the release page: <html>not found</html>", answer.headline())
    }

    @Test
    fun `a published build with no timestamp is still a version`() {
        val answer = EngineUpdateAnswer.parse("""{"ok":true,"update":false}""")
        assertEquals("Already the published build", answer.headline())
    }
}
