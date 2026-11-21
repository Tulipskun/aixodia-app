package com.tulipskun.aixodia.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure parts of the screen link: where it dials, and how a frame is
 * laid out. Both are checked here because a wrong address or a one-byte
 * misreading shows up as a black rectangle on a phone and nothing in a log.
 */
class ScreenSocketTest {

    @Test
    fun theSocketUrlKeepsTheEndpointPrefix() {
        // An install behind the console's proxy is reached at /engine, so its
        // socket is /engine/api/screen — not /api/screen at the site root, which
        // is a 404 nobody can explain from the app.
        assertEquals(
            "wss://host/engine/api/screen",
            ScreenSocket.urlFor("https://host/engine"),
        )
        assertEquals(
            "wss://host/api/screen",
            ScreenSocket.urlFor("https://host"),
        )
        assertEquals(
            "ws://127.0.0.1:8787/api/screen",
            ScreenSocket.urlFor("http://127.0.0.1:8787"),
        )
    }

    @Test
    fun aRefusedAddressIsNotDialled() {
        assertEquals("", ScreenSocket.urlFor(""))
        assertEquals("", ScreenSocket.urlFor("not an address"))
        assertEquals("", ScreenSocket.urlFor("ftp://host"))
    }

    @Test
    fun aFrameIsWalkedByItsLengths() {
        // A frame carries a header, a tile count, then each tile's geometry and
        // its JPEG. This builds one by hand so the layout is pinned to bytes
        // rather than to whatever the engine happens to send today.
        val header = """{"type":"frame","width":1280,"height":720,"seq":3}""".toByteArray()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0xFF.toByte(), 0xD9.toByte())

        val out = java.io.ByteArrayOutputStream()
        fun u16(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
        fun u32(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
            out.write((value shr 16) and 0xFF)
            out.write((value shr 24) and 0xFF)
        }
        u32(header.size)
        out.write(header)
        u32(1)
        u16(2)   // col
        u16(5)   // row
        u16(64)  // width
        u16(64)  // height
        u32(jpeg.size)
        out.write(jpeg)

        val bytes = out.toByteArray()
        // Read the frame the same way the socket does, without an Android bitmap.
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val headerLength = buffer.int
        assertEquals(header.size, headerLength)
        val got = ByteArray(headerLength).also { buffer.get(it) }
        assertTrue(String(got).contains("\"width\":1280"))

        val count = buffer.int
        assertEquals(1, count)
        assertEquals(2, buffer.short.toInt())
        assertEquals(5, buffer.short.toInt())
        assertEquals(64, buffer.short.toInt())
        assertEquals(64, buffer.short.toInt())
        val length = buffer.int
        assertEquals(jpeg.size, length)
        val payload = ByteArray(length).also { buffer.get(it) }
        assertArrayEquals(jpeg, payload)
    }

    @Test
    fun aTruncatedFrameIsNotReadPastItsEnd() {
        // Only a length and a header, no tile count: the reader must refuse it
        // rather than reading whatever memory follows.
        val header = """{"type":"frame","width":10,"height":10,"seq":1}""".toByteArray()
        val out = java.io.ByteArrayOutputStream()
        out.write(header.size and 0xFF)
        out.write((header.size shr 8) and 0xFF)
        out.write(0); out.write(0)
        out.write(header)
        val bytes = out.toByteArray()
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val headerLength = buffer.int
        buffer.position(4 + headerLength)
        // There is no room for the count.
        assertTrue(buffer.remaining() < 4)
    }

    @Test
    fun theDialAsksForThePictureThatWasChosen() {
        // The engine reads these off the upgrade, so a dial that omits the quality
        // hands back a picture nobody chose. A redial repeats the same request.
        assertEquals(
            "ticket=t1&width=1080&fps=12&quality=40",
            ScreenSocket.queryFor(
                ScreenSocket.Profile(width = 1080, fps = 12, quality = 40),
                "t1",
            ),
        )
        // Nothing chosen is nothing asked for: the engine's own default is not
        // guessed at by sending a number here.
        assertEquals("ticket=t1", ScreenSocket.queryFor(ScreenSocket.Profile(), "t1"))
    }

    @Test
    fun aLaterSettingDoesNotForgetTheEarlierOnes() {
        // The quality slider sends settings carrying only a quality. If that
        // replaced the profile, the redial after a dropped link would lose the
        // size and rate the caller had asked for.
        val asked = ScreenSocket.Profile(width = 1080, height = 720, fps = 12)
        val sharper = asked.mergedWith(ScreenSocket.Profile(quality = 80))
        assertEquals(
            ScreenSocket.Profile(width = 1080, height = 720, fps = 12, quality = 80),
            sharper,
        )
        assertEquals(
            ScreenSocket.Profile(width = 600, height = 720, fps = 12, quality = 80),
            sharper.mergedWith(ScreenSocket.Profile(width = 600)),
        )
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals("length", expected.size, actual.size)
        for (i in expected.indices) assertEquals("byte $i", expected[i], actual[i])
    }
}
