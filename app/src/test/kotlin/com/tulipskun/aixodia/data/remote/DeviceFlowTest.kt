package com.tulipskun.aixodia.data.remote

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The poll's status names, which are the Worker's, not the RFC's.
 *
 * RFC 8628 calls the waiting answer `authorization_pending`; the auth Worker
 * answers the short `pending`. Only the long name was handled, so the first poll
 * of every sign-in fell through to the failure arm and the browser's approval
 * arrived with nothing left to collect it. These pin the Worker's own answers.
 */
class DeviceFlowTest {
    private val server = FakeWorker()

    @After
    fun tearDown() {
        server.close()
    }

    private fun pollAnswering(status: String): DeviceFlow.Poll = runBlocking {
        server.respond("""{"status":"$status"}""")
        DeviceFlow.poll(server.base, "device-code")
    }

    @Test
    fun `the Worker's pending keeps the sign-in waiting`() {
        assertEquals(DeviceFlow.Poll.Pending, pollAnswering("pending"))
    }

    @Test
    fun `the RFC's long spelling is still understood`() {
        assertEquals(DeviceFlow.Poll.Pending, pollAnswering("authorization_pending"))
    }

    @Test
    fun `a delivered token is taken`() {
        server.respond("""{"status":"ok","access_token":"tok-1","token_type":"Bearer"}""")
        val answer = runBlocking { DeviceFlow.poll(server.base, "device-code") }
        assertEquals(DeviceFlow.Poll.Active("tok-1"), answer)
    }

    @Test
    fun `an expired code says so instead of retrying`() {
        server.respond("""{"status":"expired"}""")
        val answer = runBlocking { DeviceFlow.poll(server.base, "device-code") }
        assertTrue("expected a failure, got $answer", answer is DeviceFlow.Poll.Failed)
    }

    @Test
    fun `a code the Worker has never seen is a failure, not a wait`() {
        server.respond("""{"status":"not_found"}""")
        val answer = runBlocking { DeviceFlow.poll(server.base, "device-code") }
        assertTrue("expected a failure, got $answer", answer is DeviceFlow.Poll.Failed)
    }
}

/** A Worker on localhost that answers every request with one fixed body. */
internal class FakeWorker : AutoCloseable {
    private val bodies = CopyOnWriteArrayList<String>()
    private val listener = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))

    val base: String = "http://127.0.0.1:${listener.localPort}"

    init {
        threadOf {
            while (!listener.isClosed) {
                val client = runCatching { listener.accept() }.getOrNull() ?: break
                threadOf { runCatching { answer(client) } }.start()
            }
        }.start()
    }

    fun respond(body: String) {
        bodies.add(body)
    }

    private fun answer(client: Socket) {
        client.use {
            val input = it.getInputStream().bufferedReader(Charsets.UTF_8)
            input.readLine() ?: return
            var length = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.lowercase().startsWith("content-length:")) {
                    length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            var drained = 0
            while (drained < length && input.read() >= 0) drained++

            val body = bodies.last().toByteArray()
            val head = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Content-Type: application/json\r\n")
                append("Connection: close\r\n\r\n")
            }
            val out = it.getOutputStream()
            out.write(head.toByteArray())
            out.write(body)
            out.flush()
        }
    }

    private fun threadOf(block: () -> Unit) = Thread { runCatching { block() } }
        .apply { isDaemon = true; name = "FakeWorker" }

    override fun close() {
        runCatching { listener.close() }
    }
}
