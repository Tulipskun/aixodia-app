package com.tulipskun.aixodia.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the gateway's two registry writes are read as.
 *
 * The cases are the ones a reader can be misled by: a delete told "ok" about a
 * row the gateway then refused, and a delete that never reached anyone read back
 * as one that worked.
 */
class EngineAdminTest {
    @Test
    fun a_deleted_engine_names_the_row_that_went() {
        val answer = EngineDeleteAnswer.parse("""{"ok":true,"deleted":"m-900e51e3","name":"Old box"}""", 200)
        assertEquals(EngineDeleteAnswer.Deleted("m-900e51e3"), answer)
        assertEquals("Deleted m-900e51e3", answer.headline())
    }

    @Test
    fun a_live_engine_is_a_refusal_that_says_why() {
        val answer = EngineDeleteAnswer.parse(
            """{"error":"Work is still beating: stop the engine","code":"ENGINE_ONLINE"}""",
            409,
        )
        assertTrue(answer is EngineDeleteAnswer.Refused)
        answer as EngineDeleteAnswer.Refused
        assertTrue("the reader can be told to stop it", answer.online)
        assertEquals("Work is still beating: stop the engine", answer.headline())
    }

    @Test
    fun a_refusal_with_no_words_still_says_how_it_ended() {
        val answer = EngineDeleteAnswer.parse("", 502)
        assertTrue(answer is EngineDeleteAnswer.Failed)
    }

    @Test
    fun an_answer_with_no_body_is_a_failure_not_a_success() {
        val answer = EngineDeleteAnswer.parse("", 204)
        assertTrue(answer is EngineDeleteAnswer.Failed)
    }

    @Test
    fun ok_with_nothing_deleted_is_not_a_deletion() {
        // A body that says ok but names no row is the shape a bug produces, and
        // reading it as success would tell the reader a machine is gone.
        val answer = EngineDeleteAnswer.parse("""{"ok":true}""", 200)
        assertTrue(answer is EngineDeleteAnswer.Failed)
    }

    @Test
    fun a_name_is_read_back_as_the_gateway_stored_it() {
        val answer = EngineRenameAnswer.parse("""{"ok":true,"id":"m-8a7d1192","alias":"Work bench"}""", 200)
        assertEquals(EngineRenameAnswer.Saved("m-8a7d1192", "Work bench"), answer)
        assertEquals("Named Work bench", answer.headline())
    }

    @Test
    fun clearing_a_name_says_so_rather_than_saying_nothing() {
        val answer = EngineRenameAnswer.parse("""{"ok":true,"id":"m-8a7d1192","alias":""}""", 200)
        assertEquals("Name cleared", answer.headline())
    }

    @Test
    fun a_refused_name_carries_the_gateways_reason() {
        val answer = EngineRenameAnswer.parse("""{"error":"no engine named m-ghost"}""", 404)
        assertEquals(EngineRenameAnswer.Failed("no engine named m-ghost"), answer)
        assertEquals("Cannot rename: no engine named m-ghost", answer.headline())
    }

    @Test
    fun a_name_that_came_back_without_an_id_is_a_failure() {
        val answer = EngineRenameAnswer.parse("""{"ok":true,"alias":"Work"}""", 200)
        assertTrue(answer is EngineRenameAnswer.Failed)
    }

    @Test
    fun an_unreadable_reply_quotes_itself_instead_of_guessing() {
        val answer = EngineRenameAnswer.parse("<html>502 Bad Gateway</html>", 502)
        assertTrue(answer is EngineRenameAnswer.Failed)
        assertFalse(answer.headline().isBlank())
    }
}
