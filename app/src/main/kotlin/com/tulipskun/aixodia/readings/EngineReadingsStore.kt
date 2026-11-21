package com.tulipskun.aixodia.readings

import android.os.SystemClock
import com.tulipskun.aixodia.SettingsStore
import com.tulipskun.aixodia.data.remote.Health
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long a sample may go unsaved before the window is written out. */
private const val SAVE_EVERY_MS = 10_000L

/**
 * The readings behind the engine cards.
 *
 * A card's chart is a window of what this phone has seen, so it belongs to the
 * app rather than to the screen that draws it: leaving the list for a thread and
 * coming back is not a reason to forget, and neither is a process the system
 * killed while the phone was in a pocket. The window is kept here and mirrored to
 * disk, and the screen reads it the way it reads any other live value.
 *
 * Writes are throttled: a sample arrives every couple of seconds and the whole
 * window is a few hundred bytes, so it is enough to save it every few seconds
 * rather than on every animation of the chart.
 */
class EngineReadingsStore(
    private val settings: SettingsStore,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _byEngine = MutableStateFlow<Map<String, EngineReadings>>(emptyMap())
    val byEngine: StateFlow<Map<String, EngineReadings>> = _byEngine.asStateFlow()

    private var savedAt = 0L

    /** Reads the window the last run left behind. Call once, at startup. */
    suspend fun load() {
        val stored = runCatching { settings.engineReadings() }.getOrDefault("")
        if (stored.isBlank()) return
        val decoded = EngineReadings.decodeAll(stored)
        if (decoded.isNotEmpty()) _byEngine.value = decoded
    }

    /** Adds one sample to an engine's window, and saves the window now and then. */
    suspend fun record(id: String, health: Health) {
        if (id.isBlank()) return
        val current = _byEngine.value[id] ?: EngineReadings()
        _byEngine.value = _byEngine.value + (id to current.with(health))
        val stamp = now()
        if (stamp - savedAt < SAVE_EVERY_MS) return
        savedAt = stamp
        save()
    }

    /** Writes the window out whatever the interval says — used when the screen goes. */
    suspend fun flush() {
        savedAt = 0L
        save()
    }

    private suspend fun save() {
        runCatching { settings.saveEngineReadings(EngineReadings.encodeAll(_byEngine.value)) }
    }
}
