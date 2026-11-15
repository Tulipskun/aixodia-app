package com.tulipskun.aixodia.data.remote

import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.Dns

/**
 * Name resolution that never hands back an address this phone cannot reach.
 *
 * The tunnel is published under a Cloudflare name that answers with both A and
 * AAAA records. A phone on a network with no IPv6 — no address and no default
 * route, which is the ordinary state of a phone on a carrier or a hotspot that
 * only routes v4 — has every AAAA attempt refused at once. OkHttp tries the
 * addresses in the order they are returned and only moves on when a connect
 * fails, so a name whose v6 answer is unreachable spends each request's timeout
 * learning that again, and a request the engine answers in under a second
 * arrives as "offline".
 *
 * The rule is deliberately narrow: v6 is dropped only when this phone has no
 * v6 source address at all. A phone that does have one is trying to use it, and
 * its v6 path may well be the better one.
 */
object ReachableDns : Dns {

    /** Whether this phone has an IPv6 address it could send from. */
    private val hasIpv6: Boolean by lazy {
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .any { it is java.net.Inet6Address && !it.isLinkLocalAddress }
        }.getOrDefault(false)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val all = Dns.SYSTEM.lookup(hostname)
        return filterForTest(all, hasIpv6)
    }

    /** The rule itself, with the phone's own state supplied so it can be tested. */
    internal fun filterForTest(
        all: List<InetAddress>,
        phoneHasIpv6: Boolean,
    ): List<InetAddress> {
        if (phoneHasIpv6) return all
        val v4 = all.filterIsInstance<Inet4Address>()
        // An empty answer is left alone: the system's own failure says more than
        // a made-up one, and a name that resolves only to v6 is a real failure.
        return if (v4.isEmpty()) all else v4
    }
}
