package com.ninjapanda.motion.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Reliable control channel (TCP, one JSON object per line).
 *
 * Handshake: phone → [Hello] (proves it has the key), TV → [Welcome] (proves the same back,
 * and hands out the session id used in every UDP frame). Afterwards the TV pings the phone
 * to measure round-trip time and the clock offset between the two devices.
 */
@Serializable
sealed class ControlMessage {
    @Serializable @SerialName("hello")
    data class Hello(val protocol: Int, val deviceName: String, val nonce: String, val proof: String) : ControlMessage()

    @Serializable @SerialName("welcome")
    data class Welcome(val sessionId: Int, val tvName: String, val proof: String) : ControlMessage()

    @Serializable @SerialName("reject")
    data class Reject(val reason: String) : ControlMessage()

    @Serializable @SerialName("ping")
    data class Ping(val id: Int, val tvTimeMs: Long) : ControlMessage()

    @Serializable @SerialName("pong")
    data class Pong(val id: Int, val tvTimeMs: Long, val phoneTimeMs: Long) : ControlMessage()

    @Serializable @SerialName("bye")
    data object Bye : ControlMessage()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
        fun encode(message: ControlMessage): String = json.encodeToString(serializer(), message)
        fun decode(line: String): ControlMessage? = runCatching { json.decodeFromString(serializer(), line) }.getOrNull()
    }
}

internal object Handshake {
    fun helloProof(key: ByteArray, nonce: String): String =
        HmacSha256(key).mac("hello|$nonce".encodeToByteArray()).toHex()

    fun welcomeProof(key: ByteArray, nonce: String, sessionId: Int): String =
        HmacSha256(key).mac("welcome|$nonce|$sessionId".encodeToByteArray()).toHex()
}
