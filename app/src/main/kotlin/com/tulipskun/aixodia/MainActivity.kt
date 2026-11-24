package com.tulipskun.aixodia

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.tulipskun.aixodia.ui.chat.ChatScreen
import com.tulipskun.aixodia.ui.engines.EnginesScreen
import com.tulipskun.aixodia.ui.nav.Nav
import com.tulipskun.aixodia.ui.nav.Place
import com.tulipskun.aixodia.ui.theme.AIxodiaTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** When the activity left the foreground; 0 before the first pause. */
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge: let Compose handle status/nav bars via WindowInsets.
        // Without this, the system draws bars over the app (content under NavBar).
        //
        // The app is dark in every appearance, so the bars are declared dark
        // rather than following the phone: with the default styles a phone in
        // light mode drew dark status-bar icons on a black bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val c = (application as AixodiaApp).container
        applyDebugSeed(intent)
        setContent {
            AIxodiaTheme {
                Surface(color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()) {
                    // The engines list is the front door, as it is on the web.
                    // A card is checked before it is opened and becomes the
                    // active engine there; the chat is a screen of that choice.
                    //
                    // Everything above that door is one history, held here and
                    // saved through a rotation: the screen showing is whichever
                    // place is last, and back is one pop. A screen that needs to
                    // catch back for something of its own — a drawer, a menu, a
                    // keyboard — registers its own handler, and because it is
                    // composed below this one it is asked first.
                    val nav = rememberSaveable(saver = Nav.Saver) { Nav() }
                    BackHandler(enabled = nav.canGoBack) { nav.back() }
                    when (nav.current) {
                        Place.Engines -> EnginesScreen(
                            engines = c.engines,
                            readings = c.readings,
                            history = c.historyApi,
                            onOpen = { _, _, place -> nav.open(place) },
                            onOpenSettings = { nav.open(Place.Settings) },
                        )
                        // Every other place is a pane of one engine, and the
                        // chat screen is the host of them: it owns the sockets
                        // and the view model that the panes beside it share.
                        else -> ChatScreen(
                            repo = c.repo, settings = c.settings, history = c.historyApi,
                            socket = c.chat, discovery = c.discovery, engines = c.engines,
                            nav = nav,
                        )
                    }
                }
            }
        }
    }

    /**
     * A stream that died while the app was in the background must not have to
     * wait for a timeout to be noticed: coming back to the foreground is
     * exactly when the connection is likely stale, so an offline one is
     * re-established at once.
     */
    override fun onPause() {
        backgroundedAt = SystemClock.elapsedRealtime()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        val c = (application as AixodiaApp).container
        val away = if (backgroundedAt > 0L) {
            SystemClock.elapsedRealtime() - backgroundedAt
        } else {
            0L
        }
        backgroundedAt = 0L
        // The SSE stream is not replayed by a connection Android froze while
        // the app was away, and a long background trip may also mean the
        // daemon was replaced. Reconnect deliberately; the transport resumes at
        // its last sequence number.
        if (c.chat.state.value != com.tulipskun.aixodia.data.remote.ConnState.ONLINE || away >= 30_000L) {
            c.chat.reconnect()
        }
    }

    /**
     * Debug-only seeding so a device (or emulator) can be configured for a test
     * without hand-typing on a phone:
     *
     *   adb shell am start -n com.tulipskun.aixodia/.MainActivity \
     *     --es aixodia.address https://engine.aixodia.dpdns.org \
     *     --es aixodia.session work-1
     *
     * It is compiled out of release builds: BuildConfig.DEBUG is false there,
     * so a shipped APK has no way to receive connection values this way, and
     * nothing is ever baked into the binary.
     */
    private fun applyDebugSeed(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val address = intent.getStringExtra("aixodia.address").orEmpty()
        val session = intent.getStringExtra("aixodia.session").orEmpty()
        if (address.isBlank()) return
        val settings = (application as AixodiaApp).container.settings
        lifecycleScope.launch {
            if (address.isNotBlank()) settings.saveDaemon(address)
            if (session.isNotBlank()) settings.saveSession(session)
            // Clear the extras so a configuration change does not re-apply them.
            intent.removeExtra("aixodia.address")
            intent.removeExtra("aixodia.session")
        }
    }
}
