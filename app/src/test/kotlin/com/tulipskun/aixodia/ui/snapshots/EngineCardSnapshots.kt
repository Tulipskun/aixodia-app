package com.tulipskun.aixodia.ui.snapshots

import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.engines.EngineCard
import com.tulipskun.aixodia.ui.engines.EngineReadings
import com.tulipskun.aixodia.ui.theme.AIxodiaTheme
import org.junit.Rule
import org.junit.Test

/**
 * The engine card as the console draws it: the same three bands and hairlines as
 * a machine card on the web. One snapshot for a reachable engine, one for a down
 * one, so the state light and the disabled actions are locked in too.
 */
class EngineCardSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    private val online = EngineEntry(
        id = "m-8a7d1192",
        name = "Primary Engine",
        url = "https://engine.aixodia.dpdns.org/m-8a7d1192",
        subtitle = "up 12h 40m",
        tag = "primary",
        online = true,
        version = "v1.177",
    )

    private val offline = online.copy(
        id = "work-1",
        name = "Work",
        subtitle = "",
        tag = "",
        online = false,
        version = "",
    )

    @Test
    fun engineCardOnline() = paparazzi.snapshot {
        AIxodiaTheme { EngineCard(engine = online, busy = false, onOpen = {}) }
    }

    @Test
    fun engineCardOffline() = paparazzi.snapshot {
        AIxodiaTheme { EngineCard(engine = offline, busy = false, onOpen = {}) }
    }

    /**
     * A card after a few samples: the three readings over the shape of their own
     * last minutes. This is the state the screen is in five seconds after it
     * opens on a reachable engine, and the one that used to be three em dashes.
     */
    @Test
    fun engineCardWithReadings() = paparazzi.snapshot {
        AIxodiaTheme {
            EngineCard(
                engine = online,
                busy = false,
                onOpen = {},
                readings = EngineReadings(
                    cpu = listOf(8.0, 22.0, 47.0, 63.0, 31.0, 18.0, 26.0, 41.0, 55.0, 38.0, 24.0, 33.0),
                    ram = listOf(58.0, 58.0, 59.0, 59.0, 60.0, 61.0, 61.0, 62.0, 62.0, 62.0, 63.0, 62.0),
                    disk = listOf(50.0, 50.0, 50.0, 50.0, 51.0, 51.0, 51.0, 51.0, 51.0, 51.0, 51.0, 51.0),
                ),
            )
        }
    }
}
