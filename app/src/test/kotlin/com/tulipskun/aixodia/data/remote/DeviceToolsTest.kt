package com.tulipskun.aixodia.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The one reading this app needs out of an MCP tool's answer. */
class DeviceToolsTest {

    private fun answer(text: String) =
        """{"jsonrpc":"2.0","id":1,"result":{"resultType":"complete","content":[{"type":"text","text":"$text"}],"isError":false}}"""

    @Test
    fun a_screen_size_is_read_from_the_text_the_phone_sent() {
        assertEquals(900 to 1800, DeviceTools.size(answer("width=900 height=1800")))
    }

    /** A phone that does not say how big it is cannot be tapped accurately. */
    @Test
    fun an_answer_without_a_size_is_nothing_rather_than_a_guess() {
        assertNull(DeviceTools.size(answer("ok")))
        assertNull(DeviceTools.size(answer("width=0 height=1800")))
        assertNull(DeviceTools.size("not json"))
        assertNull(DeviceTools.size("""{"result":{"content":[]}}"""))
    }

    /** The text part is picked out of a list that may also carry a picture. */
    @Test
    fun a_text_part_is_found_beside_a_picture() {
        val mixed =
            """{"result":{"content":[{"type":"image","data":"AAAA"},{"type":"text","text":"width=750 height=1500"}]}}"""
        assertEquals(750 to 1500, DeviceTools.size(mixed))
    }
}
