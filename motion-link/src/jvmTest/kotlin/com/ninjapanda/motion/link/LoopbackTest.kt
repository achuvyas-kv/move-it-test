package com.ninjapanda.motion.link

import com.ninjapanda.motion.Joint
import com.ninjapanda.motion.MotionFrame
import com.ninjapanda.motion.Pose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Real UDP + TCP over localhost: the whole phone → TV path without devices. */
class LoopbackTest {
    private fun pose() = Pose(
        FloatArray(Joint.COUNT) { 0.5f }, FloatArray(Joint.COUNT) { it / 33f },
        FloatArray(Joint.COUNT), FloatArray(Joint.COUNT) { 0.9f },
    )

    @Test
    fun phoneStreamsFramesToTv(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val received = CopyOnWriteArrayList<MotionFrame>()
        val server = LinkServer("Test TV", PairingInfo.newKey(), "123456", udpPort = 47900, tcpPort = 47901) { received += it }
        try {
            server.start(scope)
            withTimeout(5_000) { server.state.first { it is LinkServer.State.Listening } }

            val client = LinkClient(server.pairingInfo("127.0.0.1"), "Test Phone")
            client.start(scope)
            withTimeout(5_000) { client.state.first { it is LinkClient.State.Connected } }
            assertEquals(LinkServer.State.Connected("Test Phone"), server.state.value)

            val start = System.currentTimeMillis()
            for (i in 0 until 60) {
                client.send(MotionFrame(start + i * 33L, 0.75f, if (i % 10 == 0) null else pose(), 12f))
                delay(10)
            }
            withTimeout(5_000) { while (received.size < 30) delay(20) }

            assertTrue(received.zipWithNext().all { (a, b) -> b.timestampMs > a.timestampMs }, "frames must arrive newest-wins, in order")
            assertTrue(received.any { it.pose == null } || received.size < 60)
            assertTrue(received.first { it.pose != null }.pose!!.visibility[0] > 0.85f)
            delay(1_200) // a couple of ping/pong rounds
            assertTrue(server.stats.value.totalReceived >= 30)
            assertTrue(server.stats.value.rttMs != null, "clock sync should have run")
            client.stop()
            withTimeout(5_000) { server.state.first { it is LinkServer.State.Listening } }
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    @Test
    fun pinPairingWorks(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = LinkServer("Test TV", PairingInfo.newKey(), "654321", udpPort = 47910, tcpPort = 47911) {}
        try {
            server.start(scope)
            withTimeout(5_000) { server.state.first { it is LinkServer.State.Listening } }
            val client = LinkClient(PairingInfo.fromPin("127.0.0.1", "654321", 47910, 47911), "Phone")
            client.start(scope)
            withTimeout(5_000) { client.state.first { it is LinkClient.State.Connected } }
            client.stop()
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    @Test
    fun wrongPinIsRejected(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = LinkServer("Test TV", PairingInfo.newKey(), "111111", udpPort = 47920, tcpPort = 47921) {}
        try {
            server.start(scope)
            withTimeout(5_000) { server.state.first { it is LinkServer.State.Listening } }
            val client = LinkClient(PairingInfo.fromPin("127.0.0.1", "222222", 47920, 47921), "Intruder")
            client.start(scope)
            val result = withTimeout(5_000) { client.state.first { it is LinkClient.State.Failed } }
            assertTrue((result as LinkClient.State.Failed).reason.contains("Wrong PIN"))
            assertEquals(LinkServer.State.Listening, server.state.value)
        } finally {
            server.stop()
            scope.cancel()
        }
    }
}
