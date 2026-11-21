package com.tulipskun.aixodia.ui.snapshots

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.R
import org.junit.Rule
import org.junit.Test

/**
 * The launcher icon as it ships: the plate and the mark, at the adaptive icon's
 * 108dp frame, drawn from the very drawables the launcher uses. The mark is
 * generated from the web's public/brand/aixodia-mark.svg, and the plate is the
 * shape and colour the manifest's background points at, so this snapshot locks
 * what a phone actually shows rather than a hand-made stand-in.
 */
class LauncherIconSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun launcherMark() = launcher(R.drawable.ic_launcher_plate)

    @Test
    fun launcherRoundMark() = launcher(R.drawable.ic_launcher_plate_round)

    /**
     * The monochrome cut, which the system tints for a themed icon. It is the
     * emblem alone — the pale disc is left out of it, because a filled disc
     * tinted in one colour is a plain circle, not a mark.
     */
    @Test
    fun launcherMonochrome() = paparazzi.snapshot {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_plate),
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_monochrome),
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
        }
    }

    private fun launcher(plate: Int) = paparazzi.snapshot {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(plate),
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(108.dp),
            )
        }
    }
}
