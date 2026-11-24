package com.tulipskun.aixodia.ui.engines

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schedule a card's word comes from.
 *
 * The two cases that produced the wrong light: an engine the registry called up
 * must still be asked (so a death is noticed within one tick), and an engine the
 * registry called down must be asked too (so a machine that started later is not
 * stuck looking dead).
 */
class ProbePlanTest {
    @Test
    fun an_engine_that_answered_is_asked_every_tick() {
        val plan = ProbePlan(retryTicks = 5)
        for (tick in 0..4) assertTrue(plan.due("a", online = true, tick = tick))
    }

    @Test
    fun an_engine_that_did_not_answer_is_asked_again_only_after_the_wait() {
        val plan = ProbePlan(retryTicks = 5)
        assertTrue("the first question is always asked", plan.due("a", online = null, tick = 0))
        for (tick in 1..4) assertFalse("no timeout every tick", plan.due("a", online = false, tick = tick))
        assertTrue("then it is asked again", plan.due("a", online = false, tick = 5))
    }

    @Test
    fun an_engine_the_registry_calls_down_is_still_asked() {
        // The bug this exists for: the old loop skipped `!engine.online`, so a
        // machine that started after the list was fetched was never asked and
        // stayed dark until someone pulled to refresh.
        val plan = ProbePlan()
        assertTrue(plan.due("b", online = false, tick = 0))
    }

    @Test
    fun a_recovered_engine_goes_back_to_every_tick_immediately() {
        val plan = ProbePlan(retryTicks = 5)
        assertTrue(plan.due("a", online = null, tick = 0))
        assertFalse(plan.due("a", online = false, tick = 1))
        assertTrue(plan.due("a", online = true, tick = 2))
        assertTrue("still up, still every tick", plan.due("a", online = true, tick = 3))
    }

    @Test
    fun the_schedule_does_not_grow_with_engines_that_have_left() {
        val plan = ProbePlan()
        plan.due("a", online = null, tick = 0)
        plan.due("b", online = null, tick = 0)
        plan.forget(listOf("b"))
        // "b" keeps its own history, "a" starts over if it ever comes back.
        assertFalse(plan.due("b", online = false, tick = 1))
        assertTrue(plan.due("a", online = null, tick = 1))
    }
}
