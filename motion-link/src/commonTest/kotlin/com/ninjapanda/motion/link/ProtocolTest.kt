package com.ninjapanda.motion.link

import com.ninjapanda.motion.Joint
import com.ninjapanda.motion.Pose
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CryptoTest {
    @Test
    fun sha256KnownVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.digest(ByteArray(0)).toHex())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.digest("abc".encodeToByteArray()).toHex())
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.digest("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun hmacRfc4231() {
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            HmacSha256(ByteArray(20) { 0x0b }).mac("Hi There".encodeToByteArray()).toHex(),
        )
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            HmacSha256("Jefe".encodeToByteArray()).mac("what do ya want for nothing?".encodeToByteArray()).toHex(),
        )
    }
}

class FrameCodecTest {
    private val key = HmacSha256(ByteArray(16) { it.toByte() })

    private fun samplePose(): Pose {
        val x = FloatArray(Joint.COUNT) { it / 40f }
        val y = FloatArray(Joint.COUNT) { 1f - it / 40f }
        val v = FloatArray(Joint.COUNT) { if (it % 3 == 0) 0.2f else 0.95f }
        x[0] = -0.3f; y[1] = 1.4f // landmarks can fall outside the frame
        return Pose(x, y, FloatArray(Joint.COUNT), v)
    }

    @Test
    fun roundTripWithPose() {
        val pose = samplePose()
        val bytes = FrameCodec.encode(FramePacket(42, 4_000_000_000L, 123_456_789L, 17, 0.75f, pose), key)
        assertEquals(205, bytes.size)
        val out = assertNotNull(FrameCodec.decode(bytes, key))
        assertEquals(42, out.sessionId)
        assertEquals(4_000_000_000L, out.sequence)
        assertEquals(123_456_789L, out.captureTimeMs)
        assertEquals(17, out.phoneLatencyMs)
        assertTrue(abs(out.aspectRatio - 0.75f) < 0.001f)
        val p = assertNotNull(out.pose)
        for (i in 0 until Joint.COUNT) {
            assertTrue(abs(p.x[i] - pose.x[i]) < 0.0002f)
            assertTrue(abs(p.y[i] - pose.y[i]) < 0.0002f)
            assertTrue(abs(p.visibility[i] - pose.visibility[i]) < 0.005f)
        }
    }

    @Test
    fun roundTripWithoutPose() {
        val bytes = FrameCodec.encode(FramePacket(1, 5, 10, 0, 1.333f, null), key)
        assertEquals(40, bytes.size)
        assertNull(assertNotNull(FrameCodec.decode(bytes, key)).pose)
    }

    @Test
    fun rejectsTamperedOrForeignPackets() {
        val bytes = FrameCodec.encode(FramePacket(1, 5, 10, 0, 0.75f, samplePose()), key)
        val tampered = bytes.copyOf().also { it[30] = (it[30] + 1).toByte() }
        assertNull(FrameCodec.decode(tampered, key))
        assertNull(FrameCodec.decode(bytes, HmacSha256(ByteArray(16) { 9 })))
        assertNull(FrameCodec.decode(bytes.copyOf(100), key))
        assertNull(FrameCodec.decode(ByteArray(205), key))
    }
}

class PairingTest {
    @Test
    fun qrRoundTrip() {
        val info = PairingInfo("192.168.1.42", 47800, 47801, PairingInfo.newKey(), "Living Room TV & co")
        val parsed = assertNotNull(PairingInfo.parse(info.toUri()))
        assertEquals("192.168.1.42", parsed.host)
        assertEquals(47800, parsed.udpPort)
        assertEquals(47801, parsed.tcpPort)
        assertTrue(parsed.key.contentEquals(info.key))
        assertEquals("Living Room TV & co", parsed.tvName)
    }

    @Test
    fun rejectsOtherQrCodes() {
        assertNull(PairingInfo.parse("https://example.com"))
        assertNull(PairingInfo.parse("moveit://pair?v=99&h=1.2.3.4&k=AAAAAAAAAAAAAAAAAAAAAA"))
        assertNull(PairingInfo.parse("moveit://pair?v=1&h=1.2.3.4&k=short"))
    }

    @Test
    fun pinKeyIsStable() {
        assertTrue(PairingInfo.pinKey("123456").contentEquals(PairingInfo.pinKey("123456")))
        assertTrue(!PairingInfo.pinKey("123456").contentEquals(PairingInfo.pinKey("123457")))
        assertEquals(6, PairingInfo.newPin().length)
    }

    @Test
    fun controlMessagesRoundTrip() {
        val hello = ControlMessage.Hello(1, "Pixel", "abc", "def")
        assertEquals(hello, ControlMessage.decode(ControlMessage.encode(hello)))
        assertEquals(ControlMessage.Bye, ControlMessage.decode(ControlMessage.encode(ControlMessage.Bye)))
        assertNull(ControlMessage.decode("not json"))
    }

    @Test
    fun clockSyncPrefersFastestRoundTrip() {
        val sync = ClockSync()
        // Phone clock is 10 000 ms ahead of the TV.
        sync.addSample(tvSendMs = 100, phoneTimeMs = 10_150, tvReceiveMs = 200) // rtt 100, noisy
        sync.addSample(tvSendMs = 300, phoneTimeMs = 10_302, tvReceiveMs = 304) // rtt 4, accurate
        assertTrue(abs(assertNotNull(sync.offsetMs) - 10_000.0) < 1.0)
    }
}
