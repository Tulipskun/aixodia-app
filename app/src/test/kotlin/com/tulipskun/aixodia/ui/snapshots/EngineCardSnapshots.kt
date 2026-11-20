package com.tulipskun.aixodia.ui.snapshots

import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.EngineEntry
import com.tulipskun.aixodia.ui.engines.EngineCard
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
}
