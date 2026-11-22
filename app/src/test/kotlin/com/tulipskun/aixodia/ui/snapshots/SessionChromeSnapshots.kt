package com.tulipskun.aixodia.ui.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.ui.screen.InputMode
import com.tulipskun.aixodia.ui.screen.SessionChrome
import org.junit.Rule
import org.junit.Test

/**
 * The session's chrome, as it will actually be drawn.
 *
 * A picture of a remote desktop would hide the thing being checked: these
 * snapshots put the rail on a flat field so the pill's weight, the 44dp items
 * and the blue behind an open pane can be compared against the viewer this
 * imitates.
 */
class SessionChromeSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    @Composable
    private fun Chrome(
        open: Boolean = false,
        mouse: Boolean = false,
        settings: Boolean = false,
        keyboard: Boolean = false,
        quality: Int = 62,
        mode: InputMode = InputMode.TRACKPAD,
        codec: String = "jpeg",
    ) {
        Box(
            Modifier
                .size(360.dp, 300.dp)
                .background(Color(0xFF2B3A4A)),
        ) {
            SessionChrome(
                open = open,
                mouse = mouse,
                settings = settings,
                keyboard = keyboard,
                quality = quality,
                mode = mode,
                codec = codec,
                onOpen = {},
                onMouse = {},
                onSettings = {},
                onKeyboard = {},
                onLeave = {},
                onScroll = {},
                onMode = {},
                onCodec = {},
                onQuality = {},
                onQualitySettled = {},
                onFit = {},
                onHold = {},
                onRelease = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun collapsed() = paparazzi.snapshot { Chrome() }

    @Test
    fun keyboardOpen() = paparazzi.snapshot { Chrome(open = true, keyboard = true) }

    @Test
    fun mousePane() = paparazzi.snapshot { Chrome(open = true, mouse = true) }

    @Test
    fun settingsPane() = paparazzi.snapshot {
        Chrome(open = true, settings = true, codec = "h264", mode = InputMode.DIRECT)
    }
}
