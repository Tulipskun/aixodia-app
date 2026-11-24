package com.tulipskun.aixodia.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.tulipskun.aixodia.SettingsStore
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Which way a two-finger drag turns the wheel.
 *
 * X has four wheel buttons: 4 and 5 are up and down, 6 and 7 left and right.
 * The engine only knows which to press if the client says which axis the
 * fingers moved along.
 */
enum class ScrollAxis(val wire: String) {
    VERTICAL("vertical"),
    HORIZONTAL("horizontal"),
}

/**
 * A terminal-shaped link to the engine's screen, instead of a shell.
 *
 * The engine sends the picture in tiles: a header, a tile count, then each
 * changed square as its own JPEG with the column and row it belongs on. Nothing
 * is sent when nothing moved, so a still screen costs no bytes at all — what
 * arrives is only what changed since the last frame the engine sent, which is
 * why a clock ticking in a corner is one tile and not the whole display.
 *
 * Fingers travel the other way as small JSON messages: a move, a press, a drag,
 * a wheel notch, a key. The engine translates those into X events, so the phone
 * drives the machine the way a mouse would.
 */
class ScreenSocket(
    private val settings: SettingsStore,
    private val clientToken: ClientToken = ClientToken.passThrough,
    private val client: OkHttpClient = defaultClient(),
) {
    /** What the engine reports once the socket is up. */
    data class Hello(
        val width: Int,
        val height: Int,
        val fps: Int,
        val tile: Int,
        val quality: Int,
        val sharedMemory: Boolean,
        /** "jpeg" or "h264": which decoder the frames need. */
        val codec: String = "jpeg",
    )

    /**
     * One frame's worth of changed tiles, already decoded.
     *
     * A tile is drawn at `(col * tile, row * tile)`. Its bitmap is exactly the
     * tile's size, so a clipped edge tile needs no padding.
     */
    data class TilePatch(
        val col: Int,
        val row: Int,
        /**
         * The grid cell this tile belongs to, in picture pixels.
         *
         * A tile against the right or bottom edge is clipped to what is left of
         * the picture, so [bitmap] is smaller than the cell. Placing it by the
         * bitmap's size would pull it toward the origin and leave the real edge
         * unpainted, so the cell size travels with the tile.
         */
        val cell: Int,
        val bitmap: Bitmap,
    )

    sealed interface Event {
        data class Ready(val hello: Hello) : Event

    /**
     * One video frame, already parsed but not decoded.
     *
     * The access unit is Annex B — start codes, not length prefixes — because
     * that is what MediaCodec reads. Decoding is left to the view, which owns
     * the decoder's lifetime.
     */
    data class Video(
        val width: Int,
        val height: Int,
        val seq: Long,
        val cursor: Pair<Int, Int>?,
        /** True when this frame stands on its own, so decoding may start here. */
        val keyframe: Boolean,
        val accessUnit: ByteArray,
    ) : Event {
        // A data class with a ByteArray gets identity equality, which is right
        // here: two frames carrying the same bytes are still two frames.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

        /**
         * A frame, and where the machine's pointer is while it is showing.
         *
         * The pointer is not in the pixels: X removes it from the framebuffer
         * before the capture ever sees it. So a client that wants to show a
         * cursor, the way a real remote desktop does, draws its own at [cursor]
         * and has to be told where that is. Null when the position is unknown.
         */
        data class Patch(
            val width: Int,
            val height: Int,
            val patches: List<TilePatch>,
            val cursor: Pair<Int, Int>? = null,
            /** The grid the tiles sit on, so [TilePatch.cell] can be trusted. */
            val tile: Int = 0,
            /** The frame's counter, so a missing frame can be noticed. */
            val seq: Long = 0,
        ) : Event
        /**
         * What the machine's pointer looks like right now.
         *
         * The phone has no pointer of its own, and the pointer it draws has to
         * change the way the machine's does: a hand over a link, an I-beam over
         * text, a watch over something loading. X keeps that as the cursor of
         * the window under the pointer, so the engine reads the pixels back with
         * XFixes and sends them here — a name cannot be used, because measured
         * on the engine's own display every cursor came back with an empty one.
         *
         * [rgba] is straight RGBA, row by row, `width * height * 4` bytes.
         */
        data class Cursor(
            /**
             * What the pointer is over: `text` when text can be typed there,
             * `pointer` for anything else.
             *
             * The machine answers this with the cursor the widget set, and the
             * engine reads it off the shape, so a tap knows whether the phone's
             * keyboard belongs on screen, and the pane knows which of its two
             * drawn shapes to show.
             */
            val role: String,
        ) : Event
        data class Failed(val reason: String) : Event
        data object Closed : Event
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var job: Job? = null
    private var decoder: Job? = null

    private val _state = MutableStateFlow(State.CLOSED)
    val state: StateFlow<State> = _state.asStateFlow()

    /** What the engine said when the link opened, kept for what later frames omit. */
    private var hello: Hello? = null

    /**
     * What the pointer is over at its last report, as the engine read it.
     *
     * Read when a tap lands: a tap into something text can be typed into brings
     * the phone's keyboard up, and a tap anywhere else puts it away. Volatile
     * because the reader thread writes it and the touch thread reads it.
     */
    @Volatile
    var cursorRole: String = ROLE_POINTER
        private set

    private val events = Channel<Event>(Channel.BUFFERED)
    val stream: Flow<Event> = events.receiveAsFlow()

    /**
     * Where the socket reader hands a frame on without decoding it.
     *
     * Decoding a frame is up to 240 JPEGs, which takes far longer than reading
     * the next message. Doing it on the reader thread lets the reader fall
     * behind, fill the socket's own buffer and be disconnected for being slow —
     * so the bytes are passed on and decoded here instead. The count is kept so
     * a backlog can be noticed rather than growing without bound.
     */
    private val inbox = Channel<ByteArray>(Channel.UNLIMITED)
    private val backlog = AtomicInteger(0)

    /**
     * Where the link dials, and what it asks for, kept so a dropped one can be
     * redialled without the caller repeating itself.
     *
     * A screen is a live view of a machine that restarts, and a phone that
     * changes network underneath it. Redialling from the last request rather
     * than from nothing is what makes the window come back on its own.
     */
    private var target: String = ""
    private var profile: Profile = Profile()

    /**
     * Which attempt is the live one.
     *
     * A redial loop and its socket outlive the call that started them, and a
     * listener for an old socket must not be able to report on the new one — or
     * close it. Every dial takes a number, and only the holder of the newest one
     * may touch the state.
     */
    private val attempt = AtomicInteger(0)

    enum class State { CLOSED, CONNECTING, OPEN }

    /**
     * Dials the engine. `url` is the engine's base address; the ticket is
     * fetched over the ordinary authenticated POST first, because a WebSocket
     * upgrade on Android cannot carry an `Authorization` header.
     *
     * The dial keeps trying: the ticket is fetched again for every attempt
     * because it is single-use, and a link that drops after the picture arrived
     * is redialled from here rather than left black.
     */
    fun connect(baseUrl: String, profile: Profile = Profile()) {
        close()
        target = baseUrl.trim()
        this.profile = profile
        val endpoint = urlFor(target)
        if (endpoint.isBlank()) {
            _state.value = State.CLOSED
            events.trySend(Event.Failed("no engine address"))
            return
        }
        _state.value = State.CONNECTING
        val mine = attempt.incrementAndGet()
        job = scope.launch {
            dialLoop(endpoint, mine)
        }
        decoder = scope.launch { drain() }
    }

    /** Redials the last address, if there was one. */
    fun reconnect() {
        if (target.isBlank()) return
        connect(target, profile)
    }

    /**
     * Dials until the link is open, and again after it drops.
     *
     * Two different failures wear the same face here: the engine refusing (its
     * ticket endpoint down, its screen gone) and the link dying under a picture
     * that was working. Waiting longer after each refusal is right for the
     * first; the second is worth retrying at once, because the usual cause is a
     * restart that has already finished.
     */
    private suspend fun CoroutineScope.dialLoop(endpoint: String, mine: Int) {
        var backoff = FIRST_RETRY_MS
        while (isActive && attempt.get() == mine) {
            val ticket = ticketFor(target)
            if (ticket.isNullOrBlank()) {
                _state.value = State.CONNECTING
                events.trySend(Event.Failed("the engine refused a screen ticket"))
                delay(jitteredBackoff(backoff, kotlin.random.Random.nextDouble()))
                backoff = minOf(backoff * 2, MAX_RETRY_MS)
                continue
            }
            // The size, rate and quality the reader chose travel on the dial as
            // well as in settings, so a redial lands on the same picture the
            // dropped one was showing.
            val link = Link(mine)
            socket = client.newWebSocket(
                Request.Builder().url("$endpoint?${queryFor(profile, ticket)}").build(),
                link,
            )
            // The engine answers an upgrade with hello at once. A silent socket
            // is a dead tunnel, not a slow picture.
            val answered = withTimeoutOrNull(READY_TIMEOUT_MS) { link.ready.await() }
            if (answered != true) {
                if (socket === link.webSocket) socket = null
                link.stop()
                if (answered == null) events.trySend(Event.Failed("no answer from the engine"))
                delay(jitteredBackoff(backoff, kotlin.random.Random.nextDouble()))
                backoff = minOf(backoff * 2, MAX_RETRY_MS)
                continue
            }
            // The picture is up. Everything from here is the link dying under a
            // working screen, which is the case worth retrying immediately.
            backoff = FIRST_RETRY_MS
            link.gone.await()
            if (!isActive || attempt.get() != mine) return
            _state.value = State.CONNECTING
            delay(REDIAL_MS)
        }
    }

    /**
     * Decodes what the reader hands over, and repairs what cannot be decoded.
     *
     * A frame is only sent when part of the screen changed, so anything lost
     * here — an overflow, a tile that will not decode, a sequence number that
     * skips — becomes a patch that stays stale until that part of the screen
     * changes again, which for a still window is never. Each of those cases asks
     * for a whole frame instead of failing quietly.
     */
    private suspend fun drain() {
        var expected = -1L
        for (bytes in inbox) {
            backlog.decrementAndGet()
            // A backlog means frames are arriving faster than they can be drawn.
            // Skipping to the newest is better than working through a queue of
            // pictures nobody will see, but the tiles that were skipped are holes,
            // so a whole frame is asked for after the skip.
            if (inbox.tryReceive().isSuccess) {
                // Discard everything queued behind this frame.
                while (inbox.tryReceive().isSuccess) backlog.decrementAndGet()
                requestKeyframe()
            }
            val frame = decode(bytes) ?: continue
            val seq = frameSeq(frame)
            // A skip in the counter means a frame never arrived: the tiles it
            // carried are still wrong on screen, and nothing will resend them
            // until they change again.
            if (expected >= 0 && seq != expected) requestKeyframe()
            expected = seq + 1
            if (!events.trySend(frame).isSuccess) {
                // The consumer is behind; the tile is dropped, so ask for the
                // frame again rather than leaving the screen half-updated.
                requestKeyframe()
            }
        }
    }

    /** The sequence number a frame carries, whichever kind it is. */
    private fun frameSeq(frame: Event): Long = when (frame) {
        is Event.Patch -> frame.seq
        is Event.Video -> frame.seq
        else -> -1L
    }

    fun close() {
        // First: nothing this link's listeners do from here is wanted, and a
        // redial loop still waiting must not open another socket behind us.
        attempt.incrementAndGet()
        socket?.close(1000, "leaving the screen")
        socket = null
        job?.cancel()
        job = null
        decoder?.cancel()
        decoder = null
        inbox.trySend(ByteArray(0))
        _state.value = State.CLOSED
    }

    /** Pointer moved. Coordinates are in the captured screen's pixels. */
    fun move(x: Int, y: Int) = send("""{"type":"move","x":$x,"y":$y}""")

    /**
     * Asks for every tile again.
     *
     * Only changed tiles are sent, so a patch that is dropped or cannot be
     * decoded is a hole that stays until that part of the screen changes — and
     * a still window may never change. Asking for a whole frame is the only way
     * to fill it. The engine answers this in JPEG mode as well as H.264.
     */
    fun requestKeyframe() = send("""{"type":"request_keyframe"}""")

    fun down(x: Int, y: Int, button: String = "left") =
        send("""{"type":"down","x":$x,"y":$y,"button":"$button"}""")

    fun up(x: Int, y: Int, button: String = "left") =
        send("""{"type":"up","x":$x,"y":$y,"button":"$button"}""")

    /**
     * A press, a walk to the destination, and a release — one message, so the
     * engine can pace the movement the machine needs to see.
     */
    fun drag(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMs: Int, button: String = "left") =
        send(
            """{"type":"drag","from_x":$fromX,"from_y":$fromY,"to_x":$toX,"to_y":$toY,""" +
                """"button":"$button","duration_ms":$durationMs}""",
        )

    fun wheel(x: Int, y: Int, delta: Int, axis: ScrollAxis = ScrollAxis.VERTICAL) =
        send("""{"type":"wheel","x":$x,"y":$y,"delta":$delta,"axis":"${axis.wire}"}""")

    fun key(keysym: String, mods: List<String> = emptyList()) {
        val held = mods.joinToString(",") { "\"$it\"" }
        send("""{"type":"key","keysym":"$keysym","mods":[$held]}""")
    }

    /**
     * Types a string on the machine, in whatever language it is written in.
     *
     * Sent as text rather than as keys because a phone can type characters the
     * machine's own keyboard has no key for — Thai, emoji — and pressing keys by
     * name would drop them silently.
     */
    fun text(value: String) {
        if (value.isEmpty()) return
        // Built with the JSON encoder rather than interpolated: a quote or a
        // backslash in the text would otherwise break the message.
        val message = org.json.JSONObject().apply {
            put("type", "text")
            put("text", value)
        }.toString()
        send(message)
    }

    /**
     * Changes the picture without redialling.
     *
     * What is asked for here becomes the request a redial repeats, so a link
     * that comes back comes back at the sharpness the reader chose rather than at
     * whatever the engine's default happens to be.
     */
    fun settings(
        width: Int? = null,
        height: Int? = null,
        fps: Int? = null,
        tile: Int? = null,
        quality: Int? = null,
        codec: String? = null,
    ) {
        val parts = buildList {
            width?.let { add("\"width\":$it") }
            height?.let { add("\"height\":$it") }
            fps?.let { add("\"fps\":$it") }
            tile?.let { add("\"tile\":$it") }
            quality?.let { add("\"quality\":$it") }
            codec?.let { add("\"codec\":\"$it\"") }
        }
        if (parts.isEmpty()) return
        profile = profile.mergedWith(Profile(width = width, height = height, fps = fps, quality = quality, codec = codec))
        send("""{"type":"settings",${parts.joinToString(",")}}""")
    }

    /**
     * How sharp this phone last asked for the picture, or null if it never chose.
     *
     * Kept beside the rest of the app's settings rather than in the screen: a
     * quality that has to be set again on every visit is a control the reader has
     * to keep reaching for.
     */
    suspend fun savedQuality(): Int? = settings.screenQuality()

    /** Records the quality, so the next visit opens at it. */
    fun keepQuality(value: Int) {
        profile = profile.mergedWith(Profile(quality = value))
        scope.launch { settings.saveScreenQuality(value) }
    }

    /**
     * Whether the picture travels as tiles or as video, for the next visit.
     *
     * A redial repeats the chosen codec through the profile, so switching stays
     * switched while the engine lasts; the store is what carries it across one.
     */
    suspend fun savedCodec(): String? = settings.screenCodec()

    fun keepCodec(name: String) {
        profile = profile.mergedWith(Profile(codec = name))
        scope.launch { settings.saveScreenCodec(name) }
    }

    /** Whether this phone pushes the pointer around or touches the screen. */
    suspend fun savedInputMode(): String? = settings.screenInputMode()

    fun keepInputMode(value: String) {
        scope.launch { settings.saveScreenInputMode(value) }
    }

    private fun send(text: String) {
        socket?.send(text)
    }

    private suspend fun ticketFor(baseUrl: String): String? = withContext(Dispatchers.IO) {
        // The ticket request authenticates with the Engine session, like every
        // other transport: the caller's token is resolved the same way the
        // terminal resolves it, so a renewal is transparent here too.
        val auth = runCatching {
            val config = settings.current()
            clientToken.get(config, settings)
        }.getOrNull() ?: return@withContext null
        val url = "${baseUrl.trimEnd('/')}/api/screen/ticket"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $auth")
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                JSONObject(body).optString("ticket").ifBlank { null }
            }
        }.getOrNull()
    }

    /**
     * One dial, and the two moments the loop waits for: it opened, and it went
     * away.
     *
     * [ready] says the engine answered with hello, which is the difference
     * between "the tunnel is dead" (retry slowly) and "the picture stopped"
     * (retry at once). [gone] is released however the link ends, including by
     * this class, so a loop waiting on it never waits for a socket that is
     * already closed.
     */
    private inner class Link(private val mine: Int) : WebSocketListener() {
        val ready = CompletableDeferred<Boolean>()
        val gone = CompletableDeferred<Unit>()
        @Volatile var webSocket: WebSocket? = null

        /** Whether this dial is still the one the caller is waiting for. */
        private fun stale(): Boolean = attempt.get() != mine

        fun stop() {
            webSocket?.cancel()
            ready.complete(false)
            gone.complete(Unit)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            this.webSocket = webSocket
            if (stale()) return
            _state.value = State.OPEN
        }

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
            if (stale()) return
            // A binary message is either a tile frame or a video frame, and which
            // one is told by the header inside it — not by the connection, because
            // the codec can change while it is open. Handed on undecoded: the
            // reader thread's job is to keep reading.
            inbox.trySend(bytes.toByteArray())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (stale()) return
            val parsed = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (parsed.optString("type")) {
                "hello" -> {
                    val greeting = Hello(
                        width = parsed.optInt("width"),
                        height = parsed.optInt("height"),
                        fps = parsed.optInt("fps"),
                        tile = parsed.optInt("tile"),
                        quality = parsed.optInt("quality"),
                        sharedMemory = parsed.optBoolean("shm"),
                        codec = parsed.optString("codec", "jpeg"),
                    )
                    hello = greeting
                    ready.complete(true)
                    events.trySend(Event.Ready(greeting))
                }
                "cursor" -> {
                    // Only the role is taken. The shape itself is the machine's
                    // bitmap, and a bitmap arrives with the place the pointer was
                    // at when the shape *changed* — a place frozen at the last
                    // change, which the app would then have to draw at a place of
                    // its own. The pane draws its own arrow and its own I-beam,
                    // so all it needs to know is which of the two belongs here.
                    cursorRole = parsed.optString("role", ROLE_POINTER)
                    events.trySend(Event.Cursor(cursorRole))
                }
                "error" -> {
                    // The engine has nothing to show, and says why once before
                    // it hangs up. That is not a picture that stopped: it is a
                    // refusal, and the loop's next try is worth waiting for.
                    ready.complete(false)
                    events.trySend(Event.Failed(parsed.optString("reason", "no screen")))
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (stale()) return
            _state.value = State.CLOSED
            // A refusal before hello is the loop's to handle; after hello the
            // picture was real and its loss is worth saying out loud.
            ready.complete(false)
            events.trySend(Event.Failed(t.message ?: "the screen link failed"))
            gone.complete(Unit)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (stale()) return
            _state.value = State.CLOSED
            ready.complete(false)
            events.trySend(Event.Closed)
            gone.complete(Unit)
        }
    }

    /**
     * Walks a frame: `u32` header length, the header JSON, `u32` tile count, and
     * then each tile as `u16 col, u16 row, u16 width, u16 height, u32 length` and
     * its JPEG.
     *
     * Returns null for a message that is not a frame, so the caller can ignore
     * it rather than showing a broken picture.
     */
    private fun decode(bytes: ByteArray): Event? {
        val frame = decodeFrame(bytes, hello)
        // A frame that could not be read whole is a set of tiles that will never
        // arrive on their own; only a whole frame fills them.
        if (frame == null) requestKeyframe()
        return frame
    }

    /**
     * Where a client is asked to set the picture; all optional.
     *
     * A nested type of the socket rather than a companion's, so it can be named
     * from outside as `ScreenSocket.Profile` — a class declared inside a
     * companion is not reachable through the outer name.
     */
    data class Profile(
        val width: Int? = null,
        val height: Int? = null,
        val fps: Int? = null,
        val quality: Int? = null,
        val codec: String? = null,
    ) {
        /**
         * Fills in what [later] asks for.
         *
         * Nothing asked for erases what was already chosen: a settings message
         * carrying only a quality must not forget the size a redial needs.
         */
        fun mergedWith(later: Profile): Profile = Profile(
            width = later.width ?: width,
            height = later.height ?: height,
            fps = later.fps ?: fps,
            quality = later.quality ?: quality,
            codec = later.codec ?: codec,
        )
    }

    companion object {
        /**
         * Reads a frame without needing a socket, so the layout can be tested.
         *
         * The recovery callback is optional here: a test wants to see that a
         * frame is refused, and the caller decides what to do about it.
         */
        internal fun decodeFrame(bytes: ByteArray, fallbackGrid: Hello?): Event? {
        if (bytes.size < 8) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val headerLength = buffer.int
        if (headerLength < 0 || headerLength > bytes.size - 8) return null
        val header = ByteArray(headerLength)
        buffer.get(header)
        val headerJson = runCatching {
            JSONObject(String(header, Charsets.UTF_8))
        }.getOrNull() ?: return null

        // A video frame stops after the header: one access unit follows.
        if (headerJson.optString("type") == "video") {
            val unit = bytes.copyOfRange(4 + headerLength, bytes.size)
            if (unit.isEmpty()) return null
            val cursor = headerJson.optJSONArray("cursor")?.let { array ->
                if (array.length() == 2) array.optInt(0) to array.optInt(1) else null
            }
            return Event.Video(
                width = headerJson.optInt("width"),
                height = headerJson.optInt("height"),
                seq = headerJson.optLong("seq"),
                cursor = cursor,
                keyframe = headerJson.optBoolean("keyframe"),
                accessUnit = unit,
            )
        }

        val grid = headerJson.optInt("tile", fallbackGrid?.tile ?: 0).let { if (it > 0) it else DEFAULT_TILE }
        val count = buffer.int
        if (count < 0 || count > MAX_TILES) return null

        val patches = ArrayList<TilePatch>(count)
        repeat(count) {
            if (buffer.remaining() < 12) return null
            val col = buffer.short.toInt()
            val row = buffer.short.toInt()
            // Read rather than skipped: an edge tile is smaller than its cell and
            // the caller needs to know which cell it was cut from.
            buffer.short.toInt()
            buffer.short.toInt()
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) return null
            val jpeg = ByteArray(length)
            buffer.get(jpeg)
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            // A tile that will not decode is a hole that stays until that tile
            // next changes, which may be never for a still window. The frame is
            // refused rather than passed on short: the caller asks for a whole
            // frame, which is the only way to fill the hole.
            if (bitmap == null) return null
            patches.add(TilePatch(col, row, grid, bitmap))
        }

        // The pointer comes as a two-element array, and only when the machine
        // could say where it was.
        val cursor = headerJson.optJSONArray("cursor")?.let { array ->
            if (array.length() == 2) array.optInt(0) to array.optInt(1) else null
        }

        return Event.Patch(
            width = headerJson.optInt("width"),
            height = headerJson.optInt("height"),
            patches = patches,
            cursor = cursor,
            tile = grid,
            seq = headerJson.optLong("seq"),
        )
        }

        /** A frame that claims more tiles than the grid could hold is refused. */
        private const val MAX_TILES = 4096

        /** The role of every shape that is not a text cursor. */
        const val ROLE_POINTER = "pointer"

        /** The role of the I-beam a widget shows when text can be typed. */
        const val ROLE_TEXT = "text"

        /** How long the engine has to answer a dial with hello. */
        private const val READY_TIMEOUT_MS = 10_000L

        /** How long a dropped picture waits for its redial; the engine is usually already back. */
        private const val REDIAL_MS = 1_000L

        /** The first wait after a refusal, doubled each time it is repeated. */
        private const val FIRST_RETRY_MS = 2_000L

        private const val MAX_RETRY_MS = 15_000L

        /**
         * The query a dial carries.
         *
         * Shared with the tests on purpose: the engine reads these parameters
         * off the upgrade — it used to ignore them — so what the app puts here is
         * the picture it actually gets.
         */
        internal fun queryFor(profile: Profile, ticket: String): String = buildString {
            append("ticket=").append(java.net.URLEncoder.encode(ticket, "UTF-8"))
            profile.width?.let { append("&width=").append(it) }
            profile.height?.let { append("&height=").append(it) }
            profile.fps?.let { append("&fps=").append(it) }
            profile.quality?.let { append("&quality=").append(it) }
            profile.codec?.let { append("&codec=").append(it) }
        }

        /**
         * The engine's own default, used only if a frame arrives before `hello`
         * or without a `tile` field — the value must never be guessed at, but a
         * placement needs some grid.
         */
        private const val DEFAULT_TILE = 64

        /**
         * Lays out a frame the way the socket does, for tests.
         *
         * Shared with the real path on purpose: a test that reimplements the
         * format can agree with itself while disagreeing with the engine.
         */
        internal fun decodeForTest(bytes: ByteArray): Event? = decodeFrame(bytes, null)

        /**
         * Its own client, like the terminal and the history API: a screen link
         * that is being torn down must not take anyone else's calls with it, and
         * one client shared by everything makes that harder than it is worth.
         *
         * The read timeout is generous on purpose — a still screen sends nothing
         * at all, which looks exactly like a stalled socket to a timeout that
         * expects a heartbeat.
         */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .dns(ReachableDns)
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        /**
         * Maps an engine address to the screen socket.
         *
         * Same rule as the terminal: keep whatever path prefix the endpoint
         * carries, because an install behind the console's proxy is reached at
         * `/engine` and its socket lives at `/engine/api/screen`.
         */
        fun urlFor(baseUrl: String): String {
            val clean = baseUrl.trim().trimEnd('/')
            val at = clean.indexOf("://")
            if (at <= 0) return ""
            val scheme = when (clean.substring(0, at).lowercase()) {
                "http", "ws" -> "ws"
                "https", "wss" -> "wss"
                else -> return ""
            }
            val host = clean.substring(at + 3).substringBefore('/')
            if (host.isBlank()) return ""
            val rest = clean.substring(at + 3).substringAfter('/', "")
            val prefix = if (rest.isBlank()) "" else "/" + rest.trim('/')
            return "$scheme://$host$prefix/api/screen"
        }
    }
}
