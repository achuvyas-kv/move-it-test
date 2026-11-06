package com.ninjapanda.motion.link

import com.ninjapanda.motion.MotionFrame
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.readByteArray
import kotlin.concurrent.Volatile
import kotlin.random.Random

/**
 * TV side of the link. Listens for one phone at a time:
 * - TCP [tcpPort]: authenticated handshake, then pings for round-trip time and clock offset.
 * - UDP [udpPort]: signed skeleton frames; the newest frame always wins, late ones are dropped.
 *
 * A new authenticated phone replaces the current one (so a phone that dropped off can rejoin).
 */
class LinkServer(
    private val tvName: String,
    /** Key embedded in the QR code. */
    private val qrKey: ByteArray,
    /** 6-digit PIN for manual pairing. */
    val pin: String,
    val udpPort: Int = LinkDefaults.UDP_PORT,
    val tcpPort: Int = LinkDefaults.TCP_PORT,
    private val clock: () -> Long = MonotonicClock::now,
    /** Called on the network thread for every accepted frame. Timestamps are in the phone's clock. */
    private val onFrame: (MotionFrame) -> Unit,
) {
    sealed interface State {
        data object Stopped : State
        data object Listening : State
        data class Connected(val phoneName: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Stopped)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _stats = MutableStateFlow(LinkStats())
    val stats: StateFlow<LinkStats> = _stats.asStateFlow()

    private val keys = listOf(qrKey, PairingInfo.pinKey(pin))

    private class Session(
        val id: Int,
        val verifier: HmacSha256,
        val peer: ByteArray?,
        val phoneName: String,
        val socket: Socket,
    ) {
        val clockSync = ClockSync()
        var lastSequence = -1L
    }

    @Volatile
    private var session: Session? = null
    private var job: Job? = null
    private var selector: SelectorManager? = null

    fun pairingInfo(host: String) = PairingInfo(host, udpPort, tcpPort, qrKey, tvName)

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val sel = SelectorManager(Dispatchers.IO)
            selector = sel
            var udp: BoundDatagramSocket? = null
            var tcp: ServerSocket? = null
            try {
                udp = aSocket(sel).udp().bind(InetSocketAddress("0.0.0.0", udpPort))
                tcp = aSocket(sel).tcp().bind("0.0.0.0", tcpPort)
                _state.value = State.Listening
                coroutineScope {
                    launch { receiveFrames(udp) }
                    while (true) {
                        val socket = tcp.accept()
                        launch { handleControl(socket) }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.value = State.Failed(e.message ?: e.toString())
            } finally {
                session = null
                udp?.close()
                tcp?.close()
                sel.close()
                if (_state.value !is State.Failed) _state.value = State.Stopped
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun handleControl(socket: Socket) {
        val read = socket.openReadChannel()
        val write = socket.openWriteChannel(autoFlush = true)
        val writeLock = Mutex()
        suspend fun send(message: ControlMessage) = writeLock.withLock { write.writeLine(ControlMessage.encode(message)) }

        var mine: Session? = null
        try {
            val hello = withTimeout(5_000) { read.readUTF8Line()?.let(ControlMessage::decode) } as? ControlMessage.Hello
                ?: return
            if (hello.protocol != LinkDefaults.PROTOCOL_VERSION) {
                send(ControlMessage.Reject("Protocol ${hello.protocol} not supported (TV speaks ${LinkDefaults.PROTOCOL_VERSION})"))
                return
            }
            val key = keys.firstOrNull { Handshake.helloProof(it, hello.nonce) == hello.proof }
            if (key == null) {
                send(ControlMessage.Reject("wrong-key"))
                return
            }

            val id = Random.Default.nextInt()
            val peer = (socket.remoteAddress as? InetSocketAddress)?.resolveAddress()
            val newSession = Session(id, HmacSha256(key), peer, hello.deviceName.take(40), socket)
            session?.socket?.close() // replace any previous phone
            session = newSession
            mine = newSession
            send(ControlMessage.Welcome(id, tvName, Handshake.welcomeProof(key, hello.nonce, id)))
            _state.value = State.Connected(newSession.phoneName)

            coroutineScope {
                val pinger = launch {
                    var n = 0
                    while (true) {
                        send(ControlMessage.Ping(n++, clock()))
                        delay(500)
                    }
                }
                while (true) {
                    val line = read.readUTF8Line() ?: break
                    when (val msg = ControlMessage.decode(line)) {
                        is ControlMessage.Pong -> newSession.clockSync.addSample(msg.tvTimeMs, msg.phoneTimeMs, clock())
                        is ControlMessage.Bye -> break
                        else -> Unit
                    }
                }
                pinger.cancel()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Connection dropped; fall through to clean-up.
        } finally {
            socket.close()
            if (mine != null && session === mine) {
                session = null
                _state.value = State.Listening
            }
        }
    }

    private suspend fun receiveFrames(udp: BoundDatagramSocket) {
        val tracker = LinkStatsTracker()
        var trackedSession = 0
        var lastPublish = 0L
        for (datagram in udp.incoming) {
            val bytes = datagram.packet.readByteArray()
            val s = session
            val now = clock()
            if (s == null) {
                tracker.rejected++
                continue
            }
            if (s.id != trackedSession) {
                tracker.reset()
                trackedSession = s.id
            }
            val packet = FrameCodec.decode(bytes, s.verifier)
            val from = (datagram.address as? InetSocketAddress)?.resolveAddress()
            if (packet == null || packet.sessionId != s.id || (s.peer != null && from != null && !from.contentEquals(s.peer))) {
                tracker.rejected++
            } else if (packet.sequence <= s.lastSequence) {
                tracker.lateDropped++ // newest wins: an older frame is useless once a newer one arrived
            } else {
                s.lastSequence = packet.sequence
                val lag = s.clockSync.offsetMs?.let { (now - (packet.captureTimeMs - it)).toFloat() }
                tracker.onFrame(now, packet.sequence, lag, packet.phoneLatencyMs)
                onFrame(MotionFrame(packet.captureTimeMs, packet.aspectRatio, packet.pose, lag ?: packet.phoneLatencyMs.toFloat()))
            }
            if (now - lastPublish >= 250) {
                lastPublish = now
                _stats.value = tracker.snapshot(now, s.clockSync.rttMs)
            }
        }
    }
}

internal suspend fun ByteWriteChannel.writeLine(text: String) {
    writeStringUtf8(text)
    writeStringUtf8("\n")
    flush()
}
