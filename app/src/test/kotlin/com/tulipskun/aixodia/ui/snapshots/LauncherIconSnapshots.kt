package com.tulipskun.aixodia.ui.snapshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
 * The launcher's foreground at the adaptive icon's full 108dp frame, rendered
 * from the very PNG the launcher uses. That PNG — and the bar's mark — are
 * generated from the web's public/brand/aixodia-mark.svg, so this snapshot
 * locks the asset that actually ships rather than a hand-made stand-in.
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
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
        }
    }
}
