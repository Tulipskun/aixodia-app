package com.tulipskun.aixodia.ui.droidime

import kotlin.math.roundToInt

/**
 * Where a finger on the picture is on the phone.
 *
 * A device's screen arrives as a picture that is smaller than the screen it was
 * taken from — the phone scales it before sending — and it is drawn into a box
 * whose shape is the app's, not the phone's, so the picture is fitted with bars.
 * A tap therefore has to cross two mappings: from the box to the picture, and
 * from the picture to the device's own pixels. Doing that in a composable would
 * make it untestable and quietly wrong at every edge, which is why it is here.
 */
object DevicePoint {

    /**
     * The rectangle a picture of [pictureWidth] x [pictureHeight] fills when it
     * is fitted into a [boxWidth] x [boxHeight] box, centred.
     */
    fun fitted(
        boxWidth: Float,
        boxHeight: Float,
        pictureWidth: Int,
        pictureHeight: Int,
    ): Rect {
        if (boxWidth <= 0f || boxHeight <= 0f || pictureWidth <= 0 || pictureHeight <= 0) {
            return Rect(0f, 0f, 0f, 0f)
        }
        val scale = minOf(boxWidth / pictureWidth, boxHeight / pictureHeight)
        val width = pictureWidth * scale
        val height = pictureHeight * scale
        return Rect(
            left = (boxWidth - width) / 2f,
            top = (boxHeight - height) / 2f,
            right = (boxWidth - width) / 2f + width,
            bottom = (boxHeight - height) / 2f + height,
        )
    }

    /**
     * The device pixel under a tap at ([x], [y]) in the box, or null when the tap
     * landed on the bars beside the picture — which is not a place on the phone,
     * and sending it would tap whatever happens to be at the clamp.
     */
    fun tap(
        x: Float,
        y: Float,
        boxWidth: Float,
        boxHeight: Float,
        pictureWidth: Int,
        pictureHeight: Int,
        deviceWidth: Int,
        deviceHeight: Int,
    ): Pixel? {
        val face = fitted(boxWidth, boxHeight, pictureWidth, pictureHeight)
        if (face.width <= 0f || face.height <= 0f) return null
        if (x < face.left || x > face.right || y < face.top || y > face.bottom) return null
        val across = (x - face.left) / face.width
        val down = (y - face.top) / face.height
        if (deviceWidth <= 0 || deviceHeight <= 0) return null
        val px = (across * deviceWidth).roundToInt().coerceIn(0, deviceWidth - 1)
        val py = (down * deviceHeight).roundToInt().coerceIn(0, deviceHeight - 1)
        return Pixel(px, py)
    }

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    data class Pixel(val x: Int, val y: Int)
}
