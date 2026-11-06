package com.ninjapanda.motion.link

import kotlin.io.encoding.Base64
import kotlin.random.Random

object LinkDefaults {
    const val PROTOCOL_VERSION = 1
    const val UDP_PORT = 47800
    const val TCP_PORT = 47801
    /** mDNS / NSD service type the TV advertises. */
    const val SERVICE_TYPE = "_moveit._tcp"
    const val SCHEME = "moveit"
}

/**
 * Everything the phone needs to reach and authenticate to a TV.
 * Shown on the TV as a QR code ([toUri]); typed in by hand as IP + PIN ([fromPin]).
 */
class PairingInfo(
    val host: String,
    val udpPort: Int = LinkDefaults.UDP_PORT,
    val tcpPort: Int = LinkDefaults.TCP_PORT,
    val key: ByteArray,
    val tvName: String = "TV",
) {
    fun toUri(): String = buildString {
        append(LinkDefaults.SCHEME).append("://pair?v=").append(LinkDefaults.PROTOCOL_VERSION)
        append("&h=").append(host)
        append("&u=").append(udpPort)
        append("&t=").append(tcpPort)
        append("&k=").append(base64.encode(key))
        append("&n=").append(percentEncode(tvName))
    }

    companion object {
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        /** Random 128-bit session key for the QR code. */
        fun newKey(): ByteArray = randomBytes(16)

        /** Random 6-digit PIN for manual pairing. */
        fun newPin(): String = Random.Default.nextInt(0, 1_000_000).toString().padStart(6, '0')

        /**
         * Key derived from a 6-digit PIN. Much weaker than the QR key (a million options),
         * but fine for a home network and needed when the QR code can't be scanned.
         */
        fun pinKey(pin: String): ByteArray = Sha256.digest("moveit-pin-v1|$pin".encodeToByteArray()).copyOf(16)

        fun fromPin(host: String, pin: String, udpPort: Int = LinkDefaults.UDP_PORT, tcpPort: Int = LinkDefaults.TCP_PORT, tvName: String = host) =
            PairingInfo(host, udpPort, tcpPort, pinKey(pin), tvName)

        /** Parses a QR payload; returns null if it isn't one of ours. */
        fun parse(uri: String): PairingInfo? {
            val prefix = "${LinkDefaults.SCHEME}://pair?"
            if (!uri.startsWith(prefix)) return null
            val params = uri.removePrefix(prefix).split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
            }.toMap()
            if (params["v"]?.toIntOrNull() != LinkDefaults.PROTOCOL_VERSION) return null
            val host = params["h"]?.takeIf { it.isNotBlank() } ?: return null
            val key = params["k"]?.let { runCatching { base64.decode(it) }.getOrNull() }?.takeIf { it.size >= 16 } ?: return null
            return PairingInfo(
                host = host,
                udpPort = params["u"]?.toIntOrNull() ?: LinkDefaults.UDP_PORT,
                tcpPort = params["t"]?.toIntOrNull() ?: LinkDefaults.TCP_PORT,
                key = key,
                tvName = params["n"]?.let(::percentDecode) ?: host,
            )
        }

        private fun percentEncode(s: String): String = buildString {
            for (b in s.encodeToByteArray()) {
                val c = b.toInt() and 0xff
                if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-._~") append(c.toChar())
                else append('%').append(c.toString(16).uppercase().padStart(2, '0'))
            }
        }

        private fun percentDecode(s: String): String {
            val out = ArrayList<Byte>()
            var i = 0
            while (i < s.length) {
                if (s[i] == '%' && i + 2 < s.length) {
                    out += s.substring(i + 1, i + 3).toInt(16).toByte(); i += 3
                } else {
                    out += s[i].code.toByte(); i++
                }
            }
            return out.toByteArray().decodeToString()
        }
    }
}
