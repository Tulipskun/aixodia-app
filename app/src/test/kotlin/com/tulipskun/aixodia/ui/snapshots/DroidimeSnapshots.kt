package com.tulipskun.aixodia.ui.snapshots

import app.cash.paparazzi.Paparazzi
import com.tulipskun.aixodia.DeviceEntry
import com.tulipskun.aixodia.ui.droidime.DeviceCard
import com.tulipskun.aixodia.ui.theme.AIxodiaTheme
import org.junit.Rule
import org.junit.Test

/**
 * A Droidime device as the console draws it: the three readings a phone has, two
 * verbs, and no shells. The dash under each reading is the point of the
 * snapshot — a device nobody has measured says "—", never "0".
 */
class DroidimeSnapshots {
    @get:Rule
    val paparazzi = Paparazzi()

    private val neo = DeviceEntry(
        id = "droidime-neo-3t",
        name = "droidime-neo-3t",
        alias = "neo 3T",
        subtitle = "neo 3T · realme",
    )

    @Test
    fun droidimeDeviceOffline() = paparazzi.snapshot {
        AIxodiaTheme { DeviceCard(device = neo, online = false, onWatch = { _, _ -> }) }
    }

    @Test
    fun droidimeDeviceOnline() = paparazzi.snapshot {
        AIxodiaTheme { DeviceCard(device = neo.copy(battery = 62), online = true, onWatch = { _, _ -> }) }
    }
}
