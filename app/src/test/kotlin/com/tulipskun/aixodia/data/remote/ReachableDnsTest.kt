package com.tulipskun.aixodia.data.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tunnel's name answers with both A and AAAA records, and a phone with no
 * IPv6 route is refused on every v6 attempt before the v4 one is tried. Asking
 * for a page then costs a timeout, and the chat screen reads that timeout as
 * "offline" even though the engine answered in under a second.
 */
class ReachableDnsTest {

    /** A resolver standing in for the system, with addresses of both families. */
    private class Fixed(private val answers: List<InetAddress>) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = answers
    }

    private fun v4(last: Int) = InetAddress.getByAddress(byteArrayOf(104, 21, 94, last.toByte())) as Inet4Address

    private fun v6(last: Int) =
        InetAddress.getByAddress(
            byteArrayOf(
                0x26, 0x06, 0x47, 0x00, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, last.toByte()
            )
        ) as Inet6Address

    @Test
    fun `a phone without a v6 address is never handed one`() {
        val answers = ReachableDns.filterForTest(listOf(v6(1), v4(2), v6(3)), phoneHasIpv6 = false)
        assertEquals(1, answers.size)
        assertTrue("the answer should be the v4 one", answers[0] is Inet4Address)
    }

    @Test
    fun `a phone with a v6 address keeps every answer in the order given`() {
        val given = listOf(v6(1), v4(2), v6(3))
        val answers = ReachableDns.filterForTest(given, phoneHasIpv6 = true)
        assertEquals(given, answers)
    }

    @Test
    fun `a name that answers only with v6 is left to fail on its own`() {
        // Emptying the list here would turn a real, explainable failure into a
        // resolver answering nothing, and OkHttp reports those differently.
        val given = listOf(v6(1))
        val answers = ReachableDns.filterForTest(given, phoneHasIpv6 = false)
        assertEquals(given, answers)
    }
}
