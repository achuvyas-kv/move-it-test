package com.ninjapanda.motion.link

import com.ninjapanda.motion.Joint
import com.ninjapanda.motion.Pose
import kotlin.math.roundToInt

/** One skeleton frame on the wire. */
class FramePacket(
    val sessionId: Int,
    /** Increases by 1 per frame; lets the TV drop late/out-of-order packets and count losses. */
    val sequence: Long,
    /** When the phone captured the camera frame, in the phone's clock. */
    val captureTimeMs: Long,
    /** Time the phone spent from camera frame to pose result. */
    val phoneLatencyMs: Int,
    val aspectRatio: Float,
    val pose: Pose?,
)

/**
 * Hand-packed binary format for skeleton frames (big-endian):
 *
 * ```
 * offset size  field
 * 0      2     magic "MP"
 * 2      1     protocol version
 * 3      1     flags (bit 0 = person present)
 * 4      4     session id
 * 8      4     sequence (unsigned)
 * 12     8     capture time ms (phone clock)
 * 20     2     phone latency ms (unsigned)
 * 22     2     aspect ratio × 10000 (unsigned)
 * 24     165   33 joints × [x int16, y int16, visibility uint8]   (only if person present)
 * ...    16    HMAC-SHA256 of everything before it, truncated
 * ```
 * x and y are stored as value × 8192, so the range is ±4 frame widths with ~0.0001 precision.
 * A full packet is 205 bytes; an empty one is 40.
 */
object FrameCodec {
    private const val MAGIC0: Byte = 'M'.code.toByte()
    private const val MAGIC1: Byte = 'P'.code.toByte()
    private const val HEADER_SIZE = 24
    private const val JOINT_SIZE = 5
    const val MAC_SIZE = 16
    const val FULL_SIZE = HEADER_SIZE + Joint.COUNT * JOINT_SIZE + MAC_SIZE
    const val EMPTY_SIZE = HEADER_SIZE + MAC_SIZE
    private const val COORD_SCALE = 8192f

    fun encode(packet: FramePacket, signer: HmacSha256): ByteArray {
        val pose = packet.pose
        val size = if (pose != null) FULL_SIZE else EMPTY_SIZE
        val out = ByteArray(size)
        val w = Writer(out)
        w.byte(MAGIC0); w.byte(MAGIC1)
        w.byte(LinkDefaults.PROTOCOL_VERSION.toByte())
        w.byte(if (pose != null) 1 else 0)
        w.int(packet.sessionId)
        w.int(packet.sequence.toInt())
        w.long(packet.captureTimeMs)
        w.short(packet.phoneLatencyMs.coerceIn(0, 0xffff))
        w.short((packet.aspectRatio * 10000f).roundToInt().coerceIn(0, 0xffff))
        if (pose != null) {
            for (i in 0 until Joint.COUNT) {
                w.short((pose.x[i] * COORD_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                w.short((pose.y[i] * COORD_SCALE).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()))
                w.byte((pose.visibility[i].coerceIn(0f, 1f) * 255f).roundToInt().toByte())
            }
        }
        val mac = signer.mac(out, 0, w.position)
        mac.copyInto(out, w.position, 0, MAC_SIZE)
        return out
    }

    /** Returns null for anything that isn't a valid, correctly signed frame. */
    fun decode(bytes: ByteArray, verifier: HmacSha256): FramePacket? {
        if (bytes.size != FULL_SIZE && bytes.size != EMPTY_SIZE) return null
        if (bytes[0] != MAGIC0 || bytes[1] != MAGIC1) return null
        if (bytes[2].toInt() != LinkDefaults.PROTOCOL_VERSION) return null
        val bodySize = bytes.size - MAC_SIZE
        if (!constantTimeEquals(bytes, bodySize, verifier.mac(bytes, 0, bodySize), MAC_SIZE)) return null

        val r = Reader(bytes, 3)
        val present = r.byte().toInt() and 1 == 1
        if (present != (bytes.size == FULL_SIZE)) return null
        val session = r.int()
        val sequence = r.int().toLong() and 0xffffffffL
        val capture = r.long()
        val latency = r.ushort()
        val aspect = r.ushort() / 10000f
        val pose = if (present) {
            val x = FloatArray(Joint.COUNT)
            val y = FloatArray(Joint.COUNT)
            val v = FloatArray(Joint.COUNT)
            for (i in 0 until Joint.COUNT) {
                x[i] = r.short() / COORD_SCALE
                y[i] = r.short() / COORD_SCALE
                v[i] = (r.byte().toInt() and 0xff) / 255f
            }
            Pose(x, y, FloatArray(Joint.COUNT), v)
        } else null
        return FramePacket(session, sequence, capture, latency, aspect, pose)
    }

    private class Writer(val buf: ByteArray) {
        var position = 0
        fun byte(v: Byte) { buf[position++] = v }
        fun short(v: Int) { byte((v shr 8).toByte()); byte(v.toByte()) }
        fun int(v: Int) { for (s in 24 downTo 0 step 8) byte((v shr s).toByte()) }
        fun long(v: Long) { for (s in 56 downTo 0 step 8) byte((v shr s).toByte()) }
    }

    private class Reader(val buf: ByteArray, var position: Int) {
        fun byte(): Byte = buf[position++]
        fun ushort(): Int = ((byte().toInt() and 0xff) shl 8) or (byte().toInt() and 0xff)
        fun short(): Int = ushort().toShort().toInt()
        fun int(): Int {
            var v = 0
            repeat(4) { v = (v shl 8) or (byte().toInt() and 0xff) }
            return v
        }
        fun long(): Long {
            var v = 0L
            repeat(8) { v = (v shl 8) or (byte().toLong() and 0xff) }
            return v
        }
    }
}
