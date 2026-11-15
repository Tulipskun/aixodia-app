package com.tulipskun.aixodia.data.remote

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * Name resolution that never hands back an address this phone cannot reach, and
 * never gives up because the phone's default network cannot answer at all.
 *
 * Two different faults made the engine look offline while it was answering in
 * under a second.
 *
 * The tunnel's name has both A and AAAA records. A phone with no IPv6 — no
 * address and no v6 default route — is refused on every AAAA attempt, and
 * OkHttp tries addresses in the order they arrive and only moves on after a
 * connect fails, so every request spent its first attempt on a v6 path that
 * could not work.
 *
 * The second fault is a VPN that is the default network and publishes no DNS
 * servers. Tailscale in that state reports `DnsAddresses: []`, and every lookup
 * through it fails with "No address associated with hostname" even though the
 * Wi-Fi underneath resolves the name fine. The app signed in and then sat at
 * "offline" with a resolvable engine one hop away.
 *
 * So: v6 answers are dropped only when the phone has no v6 source address, and
 * a lookup the system cannot answer is retried against public resolvers before
 * the failure is reported.
 */
object ReachableDns : Dns {

    /**
     * The phone's connectivity manager, set once by the app.
     *
     * Android refuses a plain UDP query to a public resolver from an ordinary
     * app, and a VPN can own the default route, so the only way to ask the
     * network underneath is to bind the socket to that network by handle. The
     * manager is held here rather than passed in because [Dns] is built by
     * OkHttp, which has no place to carry it.
     */
    @Volatile
    var connectivity: android.net.ConnectivityManager? = null

    /** Resolvers asked only when the phone's own cannot answer. */
    private val FALLBACK = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8")

    /** Whether this phone has an IPv6 address it could send from. */
    private val hasIpv6: Boolean by lazy {
        runCatching {
            java.net.NetworkInterface.getNetworkInterfaces()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .any { it is Inet6Address && !it.isLinkLocalAddress }
        }.getOrDefault(false)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // The system's own resolver is asked first, because on a healthy phone
        // it is the right one and knows local names this code does not. It is
        // not trusted to be the only one: a VPN that is the default network can
        // publish itself as the sole resolver and answer nothing for a name the
        // Wi-Fi underneath resolves, and some Android builds report that as an
        // empty list rather than an exception.
        val system = runCatching { Dns.SYSTEM.lookup(hostname) }.getOrNull()
        if (!system.isNullOrEmpty()) return filterForTest(system, hasIpv6)
        val asked = askPublicResolvers(hostname)
        if (!asked.isNullOrEmpty()) return filterForTest(asked, hasIpv6)
        // Nothing answered: report the system's own failure when there was one,
        // since it names the phone's network rather than a resolver.
        system?.let { return it }
        throw UnknownHostException("no resolver could answer for $hostname")
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

    /**
     * Asks named resolvers directly, in order, and returns the first answer.
     *
     * A null answer means every resolver failed or none knew the name; the
     * caller then reports the system's own error, which names the phone's
     * network rather than a resolver nobody asked about.
     */
    private fun askPublicResolvers(hostname: String): List<InetAddress>? {
        for (server in FALLBACK) {
            val answer = runCatching { query(hostname, server) }.getOrNull()
            if (!answer.isNullOrEmpty()) return answer
        }
        return null
    }

    /** One A-record query over UDP, using the platform resolver for the server. */
    private fun query(hostname: String, server: String): List<InetAddress> {
        val id = (System.nanoTime() and 0xFFFF).toInt()
        val packet = buildQuery(id, hostname)
        val network = nonVpnNetwork()
        java.net.DatagramSocket().use { socket ->
            socket.soTimeout = 2_000
            // Sending the ordinary way would go to the VPN's resolver, which is
            // the one that just answered nothing. Android also refuses an
            // unbound UDP query from an ordinary app, so binding to the network
            // itself is what makes the query legal as well as correctly routed.
            if (network != null) runCatching { network.bindSocket(socket) }
            socket.send(java.net.DatagramPacket(packet, packet.size, InetAddress.getByName(server), 53))
            val buffer = ByteArray(1024)
            val reply = java.net.DatagramPacket(buffer, buffer.size)
            socket.receive(reply)
            return parseAnswer(buffer, reply.length, id)
        }
    }

    /** The active network that is not a VPN, when there is one. */
    private fun nonVpnNetwork(): android.net.Network? {
        val manager = connectivity ?: return null
        return runCatching {
            manager.allNetworks.firstOrNull { candidate ->
                val caps = manager.getNetworkCapabilities(candidate) ?: return@firstOrNull false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
        }.getOrNull()
    }

    private fun buildQuery(id: Int, hostname: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write((id shr 8) and 0xFF)
        out.write(id and 0xFF)
        out.write(0x01) // recursion desired
        out.write(0x00)
        out.write(0x00); out.write(0x01) // one question
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        for (label in hostname.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0x00)
        out.write(0x00); out.write(0x01) // type A
        out.write(0x00); out.write(0x01) // class IN
        return out.toByteArray()
    }

    private fun parseAnswer(data: ByteArray, length: Int, id: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val replyId = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        if (replyId != id) return emptyList()
        val answers = ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        if (answers == 0) return emptyList()
        var at = 12
        // Skip the question: labels, then type and class.
        while (at < length && data[at].toInt() != 0) {
            if ((data[at].toInt() and 0xC0) == 0xC0) { at += 2; break }
            at += (data[at].toInt() and 0xFF) + 1
        }
        if (at < length && data[at].toInt() == 0) at++
        at += 4
        val found = ArrayList<InetAddress>(answers)
        repeat(answers) {
            if (at >= length) return@repeat
            if ((data[at].toInt() and 0xC0) == 0xC0) at += 2 else {
                while (at < length && data[at].toInt() != 0) at += (data[at].toInt() and 0xFF) + 1
                at++
            }
            if (at + 10 > length) return@repeat
            val type = ((data[at].toInt() and 0xFF) shl 8) or (data[at + 1].toInt() and 0xFF)
            val rdLength = ((data[at + 8].toInt() and 0xFF) shl 8) or (data[at + 9].toInt() and 0xFF)
            at += 10
            if (type == 1 && rdLength == 4 && at + 4 <= length) {
                found.add(InetAddress.getByAddress(data.copyOfRange(at, at + 4)))
            }
            at += rdLength
        }
        return found
    }
}
