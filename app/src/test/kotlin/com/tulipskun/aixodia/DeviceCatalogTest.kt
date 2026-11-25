package com.tulipskun.aixodia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCatalogTest {
    @Test
    fun a_device_lists_with_the_name_a_person_chose() {
        val devices = DeviceCatalog.parse(
            """{"devices":[{"id":"droidime-neo-3t","name":"droidime-neo-3t","alias":"neo 3T",
                "subtitle":"neo 3T · realme","battery":62,"online":true}]}""",
        )
        assertEquals(1, devices.size)
        assertEquals("neo 3T", devices[0].displayName)
        assertEquals(62, devices[0].battery)
        assertEquals(true, devices[0].online)
    }

    @Test
    fun an_empty_alias_draws_the_device_own_name() {
        val devices = DeviceCatalog.parse("""{"devices":[{"id":"d","name":"i2405","alias":""}]}""")
        assertEquals("i2405", devices[0].displayName)
    }

    @Test
    fun a_null_battery_is_no_reading_and_a_dash_is_drawn() {
        // A device nobody has measured is not a device at zero: the card must have
        // nothing to draw, not a number it invented.
        val devices = DeviceCatalog.parse("""{"devices":[{"id":"d","name":"n","battery":null}]}""")
        assertNull(devices[0].battery)
    }

    @Test
    fun an_engine_list_is_not_a_device_list() {
        // The two routes and the two shapes stay apart: an answer with `engines`
        // in it names no devices, whatever else it says.
        val devices = DeviceCatalog.parse(
            """{"engines":[{"id":"m-8a7d1192","name":"zero","url":"https://e/zero"}]}""",
        )
        assertEquals(0, devices.size)
    }

    @Test
    fun a_broken_body_is_an_empty_list_and_not_a_crash() {
        assertEquals(0, DeviceCatalog.parse("not json").size)
        assertEquals(0, DeviceCatalog.parse("").size)
    }
}
