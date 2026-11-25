package com.tulipskun.aixodia

import android.app.Application
import com.tulipskun.aixodia.data.local.AppDatabase
import com.tulipskun.aixodia.data.remote.ChatTransport
import com.tulipskun.aixodia.data.remote.ClientToken
import com.tulipskun.aixodia.data.remote.DaemonDiscovery
import com.tulipskun.aixodia.data.remote.DeviceDirectory
import com.tulipskun.aixodia.data.remote.EngineDirectory
import com.tulipskun.aixodia.data.remote.HistoryApi
import com.tulipskun.aixodia.data.remote.RunSocket
import com.tulipskun.aixodia.data.repo.ChatRepository
import com.tulipskun.aixodia.readings.EngineReadingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AixodiaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // The phone's default network can be a VPN that answers no names, so the
        // resolver needs the manager to ask the network underneath it.
        com.tulipskun.aixodia.data.remote.ReachableDns.connectivity =
            getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        container = AppContainer(this)
        // Observe the Engine's announced URL and health state while the app is
        // running. The default Named Tunnel URL is already configured locally;
        // discovery never reads Cloudflare KV or requires a Cloudflare token.
        container.discovery.start()
        // The engine list the phone switched with last time is on disk; this
        // reads the current one in the background, so the switcher is fresh by
        // the first time it is opened and still works when it is not.
        container.refreshEngines()
        // Likewise the charts: an engine card that opens on an empty strip after
        // every restart is a card that forgot what it had already seen.
        container.loadReadings()
    }
}

class AppContainer(app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = AppDatabase.get(app)
    val settings = SettingsStore(app)
    // One app-wide provider: API calls pass the already-issued Engine session.
    // GitHub OAuth is used only during the explicit sign-in exchange.
    val clientToken: ClientToken = ClientToken.shared
    val historyApi = HistoryApi(settings, clientToken = clientToken)
    // No shared socket: chat is POST + SSE and the terminal is its own
    // WebSocket. Discovery only has to nudge the run stream when the engine
    // address changes — and the run stream reads the announced address itself
    // when a dial fails, since the announcements travel on that dead socket.
    val chat: ChatTransport = RunSocket(settings, historyApi, app, clientToken)
    val repo = ChatRepository(db, historyApi, chat)
    val discovery = DaemonDiscovery(settings, historyApi, chat, scope)
    val engines = EngineDirectory(settings)
    // The phones DroidIME runs on: a list of its own, read from a route of its own.
    val devices = DeviceDirectory(settings)
    // The windows behind the cards' charts. Loaded from disk once, so a restart
    // opens on the same strips instead of an empty one that fills in from now.
    val readings = EngineReadingsStore(settings)

    /** Reads the stored readings without holding up the first screen. */
    fun loadReadings() {
        scope.launch { readings.load() }
    }

    /** Reads the gateway's engine list without holding up the first screen. */
    fun refreshEngines() {
        scope.launch { engines.refresh() }
    }
}
