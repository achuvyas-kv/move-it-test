package com.ninjapanda.motion.link

import com.ninjapanda.motion.MotionFrame
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer

/**
 * Phone side of the link: connects to the TV described by [pairing], authenticates,
 * then streams every [send] frame over UDP. Reconnects automatically if the link drops,
 * unless the TV rejected the key.
 */
class LinkClient(
    private val pairing: PairingInfo,
    private val deviceName: String,
    /** Must be the same clock as [MotionFrame.timestampMs] (SystemClock::uptimeMillis on Android). */
    private val clock: () -> Long = MonotonicClock::now,
) {
    sealed interface State {
        data object Idle : State
        data class Connecting(val attempt: Int, val lastError: String?) : State
        data class Connected(val tvName: String) : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _framesSent = MutableStateFlow(0L)
    val framesSent: StateFlow<Long> = _framesSent.asStateFlow()

    // Conflated: if the network is momentarily slow, only the newest frame is kept.
    private val outbox = Channel<MotionFrame>(Channel.CONFLATED)
    private var job: Job? = null

    private class Rejected(val reason: String) : Exception(reason)

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val selector = SelectorManager(Dispatchers.IO)
            try {
                var attempt = 0
                var lastError: String? = null
                while (isActive) {
                    _state.value = State.Connecting(attempt, lastError)
                    try {
                        runSession(selector)
                        lastError = "TV closed the connection"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Rejected) {
                        _state.value = State.Failed(
                            if (e.reason == "wrong-key") "Wrong PIN or old QR code. Pair again." else e.reason,
                        )
                        return@launch
                    } catch (e: Throwable) {
                        lastError = e.message ?: e.toString()
                    }
                    attempt++
                    delay(if (attempt < 3) 1_000 else 3_000)
                }
            } finally {
                selector.close()
                if (_state.value !is State.Failed) _state.value = State.Idle
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** Non-blocking; call from the pose callback. Dropped when not connected. */
    fun send(frame: MotionFrame) {
        if (_state.value is State.Connected) outbox.trySend(frame)
    }

    private suspend fun runSession(selector: SelectorManager) {
        val tcp = withTimeout(5_000) { aSocket(selector).tcp().connect(pairing.host, pairing.tcpPort) }
        try {
            val read = tcp.openReadChannel()
            val write = tcp.openWriteChannel(autoFlush = true)
            val writeLock = Mutex()
            suspend fun sendControl(m: ControlMessage) = writeLock.withLock { write.writeLine(ControlMessage.encode(m)) }

            val nonce = randomBytes(16).toHex()
            sendControl(ControlMessage.Hello(LinkDefaults.PROTOCOL_VERSION, deviceName, nonce, Handshake.helloProof(pairing.key, nonce)))
            val welcome = when (val reply = withTimeout(5_000) { read.readUTF8Line()?.let(ControlMessage::decode) }) {
                is ControlMessage.Welcome -> reply
                is ControlMessage.Reject -> throw Rejected(reply.reason)
                else -> throw IllegalStateException("No reply from TV")
            }
            if (welcome.proof != Handshake.welcomeProof(pairing.key, nonce, welcome.sessionId)) {
                throw Rejected("TV could not prove it has the pairing key")
            }

            val signer = HmacSha256(pairing.key)
            val udp = aSocket(selector).udp().connect(InetSocketAddress(pairing.host, pairing.udpPort))
            try {
                _state.value = State.Connected(welcome.tvName)
                coroutineScope {
                    val sender = launch {
                        var sequence = 0L
                        for (frame in outbox) {
                            val bytes = FrameCodec.encode(
                                FramePacket(
                                    sessionId = welcome.sessionId,
                                    sequence = sequence++,
                                    captureTimeMs = frame.timestampMs,
                                    phoneLatencyMs = frame.latencyMs.toInt(),
                                    aspectRatio = frame.aspectRatio,
                                    pose = frame.pose,
                                ),
                                signer,
                            )
                            udp.send(Datagram(Buffer().apply { write(bytes) }, udp.remoteAddress))
                            _framesSent.value++
                        }
                    }
                    while (true) {
                        val line = read.readUTF8Line() ?: break
                        when (val msg = ControlMessage.decode(line)) {
                            is ControlMessage.Ping -> sendControl(ControlMessage.Pong(msg.id, msg.tvTimeMs, clock()))
                            is ControlMessage.Reject -> throw Rejected(msg.reason)
                            is ControlMessage.Bye -> break
                            else -> Unit
                        }
                    }
                    sender.cancel()
                }
            } finally {
                udp.close()
            }
        } finally {
            tcp.close()
        }
    }
}
