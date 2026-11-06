package com.ninjapanda.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotionProcessorTest {

    @Test
    fun calibratesAfterStandingStill() {
        val sim = Sim()
        sim.frame(SyntheticBody(), 10)
        assertEquals(TrackingState.Calibrating, sim.last.tracking)
        sim.frame(SyntheticBody(), 50)
        assertEquals(TrackingState.Ready, sim.last.tracking)
        assertEquals(1, sim.count<MotionEvent.Calibrated>())
    }

    @Test
    fun noPersonAndPartialVisibility() {
        val sim = Sim()
        assertEquals(TrackingState.NoPerson, sim.frame(null).tracking)
        val hidden = SyntheticBody().pose().also { it.visibility[Joint.LEFT_HIP.ordinal] = 0.1f }
        sim.timeMs += 33
        assertEquals(TrackingState.NotFullyVisible, sim.processor.process(MotionFrame(sim.timeMs, 0.75f, hidden)).tracking)
    }

    @Test
    fun tooCloseAndTooFar() {
        assertEquals(TrackingState.TooClose, Sim().frame(SyntheticBody(torso = 0.5f, hipY = 0.7f), 5).tracking)
        assertEquals(TrackingState.TooFar, Sim().frame(SyntheticBody(torso = 0.05f), 5).tracking)
    }

    @Test
    fun standingWithJitterProducesNoEvents() {
        val sim = Sim()
        sim.calibrate()
        for (i in 0 until 150) sim.frame(SyntheticBody(noise = 0.004f, seed = i))
        assertEquals(emptyList(), sim.events)
        assertEquals(Posture.Standing, sim.last.posture)
        assertEquals(Lane.Center, sim.last.lane)
    }

    @Test
    fun jumpFiresOnceAndLands() {
        val sim = Sim()
        sim.calibrate()
        sim.frame(SyntheticBody(hipY = 0.6f - 0.3f * 0.2f), 8) // hips up by 0.3 torso
        assertEquals(Posture.Jumping, sim.last.posture)
        sim.frame(SyntheticBody(), 30)
        assertEquals(1, sim.count<MotionEvent.Jump>())
        assertEquals(Posture.Standing, sim.last.posture)
    }

    @Test
    fun duckStartsAndEnds() {
        val sim = Sim()
        sim.calibrate()
        sim.frame(SyntheticBody(shoulderDrop = 0.4f * 0.2f), 15)
        assertEquals(Posture.Ducking, sim.last.posture)
        assertEquals(1, sim.count<MotionEvent.DuckStart>())
        sim.frame(SyntheticBody(), 30)
        assertEquals(1, sim.count<MotionEvent.DuckEnd>())
        assertEquals(Posture.Standing, sim.last.posture)
    }

    @Test
    fun squatCountsAsDuck() {
        val sim = Sim()
        sim.calibrate()
        sim.frame(SyntheticBody(hipY = 0.6f + 0.45f * 0.2f), 15) // whole body lower
        assertEquals(Posture.Ducking, sim.last.posture)
    }

    @Test
    fun steppingSidewaysChangesLane() {
        val sim = Sim()
        sim.calibrate()
        val step = 0.5f * 0.2f / 0.75f // half a torso, in frame-width units
        sim.frame(SyntheticBody(centerX = 0.5f + step), 15)
        assertEquals(Lane.Right, sim.last.lane)
        assertTrue(sim.last.lean > 0.5f)
        sim.frame(SyntheticBody(), 20)
        sim.frame(SyntheticBody(centerX = 0.5f - step), 15)
        assertEquals(Lane.Left, sim.last.lane)
        assertEquals(
            listOf(Lane.Center to Lane.Right, Lane.Right to Lane.Center, Lane.Center to Lane.Left),
            sim.events.filterIsInstance<MotionEvent.LaneChanged>().map { it.from to it.to },
        )
    }

    @Test
    fun fastHandSwipeIsASlash() {
        val sim = Sim()
        val y = 0.35f
        sim.calibrate(SyntheticBody(rightHand = Vec2(0.2f, y)))
        for (i in 1..8) sim.frame(SyntheticBody(rightHand = Vec2(0.2f + 0.65f * i / 8f, y)))
        sim.frame(SyntheticBody(rightHand = Vec2(0.85f, y)), 10)

        val slashes = sim.events.filterIsInstance<MotionEvent.Slash>()
        assertEquals(1, slashes.size)
        assertEquals(HandSide.Right, slashes[0].side)
        assertTrue(slashes[0].end.x > slashes[0].start.x, "slash should go left → right")
        assertTrue(!sim.last.rightHand.bladeActive)
    }

    @Test
    fun slowHandMovementIsNotASlash() {
        val sim = Sim()
        sim.calibrate(SyntheticBody(rightHand = Vec2(0.2f, 0.35f)))
        for (i in 0..60) sim.frame(SyntheticBody(rightHand = Vec2(0.2f + 0.65f * i / 60f, 0.35f)))
        assertEquals(0, sim.count<MotionEvent.Slash>())
        assertTrue(sim.last.rightHand.visible)
    }

    @Test
    fun handCursorCoversReachBox() {
        val sim = Sim()
        sim.calibrate()
        // Hand straight out to the right at shoulder height, at the edge of the reach box.
        val s = sim.frame(SyntheticBody(rightHand = Vec2(0.5f + 1.5f * 0.2f / 0.75f, 0.4f)), 20)
        assertTrue(s.rightHand.cursor.x > 0.95f, "cursor x = ${s.rightHand.cursor.x}")
        assertTrue(s.rightHand.cursor.y in 0.5f..0.7f, "cursor y = ${s.rightHand.cursor.y}")
    }

    @Test
    fun losingThePlayerForcesRecalibration() {
        val sim = Sim()
        sim.calibrate()
        sim.frame(null, 60)
        assertEquals(1, sim.count<MotionEvent.PlayerLost>())
        assertEquals(TrackingState.Calibrating, sim.frame(SyntheticBody()).tracking)
    }

    @Test
    fun briefDropoutKeepsCalibration() {
        val sim = Sim()
        sim.calibrate()
        sim.frame(null, 10)
        assertEquals(TrackingState.Ready, sim.frame(SyntheticBody()).tracking)
    }
}
