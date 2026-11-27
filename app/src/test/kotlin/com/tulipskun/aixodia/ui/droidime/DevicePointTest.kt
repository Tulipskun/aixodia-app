package com.tulipskun.aixodia.ui.droidime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Where a finger on a fitted picture is on the phone it was taken from. */
class DevicePointTest {

    /** A picture that fits the box's aspect exactly fills it: no bars, no scale surprise. */
    @Test
    fun a_picture_of_the_boxes_shape_maps_corner_to_corner() {
        val corner = DevicePoint.tap(
            x = 0f, y = 0f,
            boxWidth = 900f, boxHeight = 1800f,
            pictureWidth = 750, pictureHeight = 1500,
            deviceWidth = 900, deviceHeight = 1800,
        )
        assertEquals(DevicePoint.Pixel(0, 0), corner)

        val far = DevicePoint.tap(
            x = 900f, y = 1800f,
            boxWidth = 900f, boxHeight = 1800f,
            pictureWidth = 750, pictureHeight = 1500,
            deviceWidth = 900, deviceHeight = 1800,
        )
        assertEquals(DevicePoint.Pixel(899, 1799), far)
    }

    /**
     * A phone is taller than the pane, so the picture is fitted with bars on the
     * sides: the middle of the picture is the middle of the phone, and a tap on a
     * bar is not a place on the phone at all.
     */
    @Test
    fun bars_beside_a_fitted_picture_are_not_the_phone() {
        // A 1000x1000 box with a 500x1000 picture: 250 px of bar on each side.
        val face = DevicePoint.fitted(1000f, 1000f, 500, 1000)
        assertEquals(250f, face.left, 0.01f)
        assertEquals(750f, face.right, 0.01f)

        val middle = DevicePoint.tap(
            x = 500f, y = 500f,
            boxWidth = 1000f, boxHeight = 1000f,
            pictureWidth = 500, pictureHeight = 1000,
            deviceWidth = 900, deviceHeight = 1800,
        )
        assertEquals(DevicePoint.Pixel(450, 900), middle)

        assertNull(
            "a tap on the bar is not a tap on the phone",
            DevicePoint.tap(
                x = 100f, y = 500f,
                boxWidth = 1000f, boxHeight = 1000f,
                pictureWidth = 500, pictureHeight = 1000,
                deviceWidth = 900, deviceHeight = 1800,
            ),
        )
    }

    /** Nothing measured is nothing to send: a tap before the size is known goes nowhere. */
    @Test
    fun a_tap_before_the_phone_has_said_its_size_is_dropped() {
        assertNull(
            DevicePoint.tap(
                x = 10f, y = 10f,
                boxWidth = 900f, boxHeight = 1800f,
                pictureWidth = 750, pictureHeight = 1500,
                deviceWidth = 0, deviceHeight = 0,
            ),
        )
        assertNull(
            DevicePoint.tap(
                x = 10f, y = 10f,
                boxWidth = 0f, boxHeight = 0f,
                pictureWidth = 750, pictureHeight = 1500,
                deviceWidth = 900, deviceHeight = 1800,
            ),
        )
    }
}
