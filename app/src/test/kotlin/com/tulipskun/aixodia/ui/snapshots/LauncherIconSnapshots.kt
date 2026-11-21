package com.tulipskun.aixodia.ui.snapshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.R
import org.junit.Rule
import org.junit.Test

/**
 * The launcher's mark on its own recess, at the adaptive icon's full 108dp frame.
 * It must be the same mark the bar and the web show, and its outer ring must stay
 * inside the safe zone so no launcher mask cuts it.
 *
 * Paparazzi cannot inflate a layer-list through painterResource, so this draws
 * the mark the layer-list points at with the same 16dp inset it applies. The
 * layer-list's own reference is checked by resource processing, not here.
 */
class LauncherIconSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun launcherMark() = paparazzi.snapshot {
        Box(
            Modifier.size(108.dp).background(Color(0xFF0B1220)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.aixodia_mark),
                contentDescription = null,
                modifier = Modifier.size(108.dp).padding(16.dp),
            )
        }
    }
}
