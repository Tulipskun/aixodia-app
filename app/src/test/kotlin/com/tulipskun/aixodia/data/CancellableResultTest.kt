package com.tulipskun.aixodia.data

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leaving a screen cancels the work it started. That is not a failure, and it
 * must never be reported as one: a settings page that says "The coroutine scope
 * left the composition" is telling the reader about a Compose detail as if the
 * engine had refused something.
 */
class CancellableResultTest {

    @Test
    fun a_failure_is_reported_as_a_failure() {
        val result = runCatchingCancellable<Int> { error("no route to host") }
        assertEquals("no route to host", result.exceptionOrNull()?.message)
    }

    @Test
    fun a_cancellation_stops_the_caller_instead_of_answering() {
        var threw = false
        try {
            runCatchingCancellable<Int> { throw CancellationException("The coroutine scope left the composition") }
        } catch (cancelled: CancellationException) {
            threw = true
        }
        assertTrue("a cancellation must not be turned into a Result", threw)
    }

    @Test
    fun success_is_carried_through() {
        assertEquals(7, runCatchingCancellable { 7 }.getOrNull())
    }
}
