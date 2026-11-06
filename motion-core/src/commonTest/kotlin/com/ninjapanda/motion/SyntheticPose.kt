package com.ninjapanda.motion

/**
 * Builds plausible standing poses in frame coordinates (portrait 3:4 camera),
 * so detectors can be tested without a camera.
 */
data class SyntheticBody(
    val centerX: Float = 0.5f,
    val hipY: Float = 0.6f,
    val torso: Float = 0.2f,
    /** Extra downward shift of shoulders and head only (bending / ducking). */
    val shoulderDrop: Float = 0f,
    val rightHand: Vec2? = null,
    val leftHand: Vec2? = null,
    val noise: Float = 0f,
    val seed: Int = 0,
) {
    fun pose(): Pose {
        val x = FloatArray(Joint.COUNT)
        val y = FloatArray(Joint.COUNT)
        val z = FloatArray(Joint.COUNT)
        val v = FloatArray(Joint.COUNT) { 0.99f }
        val shoulderY = hipY - torso + shoulderDrop
        fun set(j: Joint, px: Float, py: Float) {
            x[j.ordinal] = px
            y[j.ordinal] = py
        }
        // Head
        val headY = shoulderY - 0.1f
        Joint.entries.filter { it.ordinal <= Joint.MOUTH_RIGHT.ordinal }.forEach { set(it, centerX, headY) }
        // Arms: player's right appears on the screen's right (mirrored front camera)
        set(Joint.LEFT_SHOULDER, centerX - 0.08f, shoulderY); set(Joint.RIGHT_SHOULDER, centerX + 0.08f, shoulderY)
        set(Joint.LEFT_ELBOW, centerX - 0.1f, shoulderY + 0.12f); set(Joint.RIGHT_ELBOW, centerX + 0.1f, shoulderY + 0.12f)
        val lh = leftHand ?: Vec2(centerX - 0.1f, shoulderY + 0.22f)
        val rh = rightHand ?: Vec2(centerX + 0.1f, shoulderY + 0.22f)
        listOf(Joint.LEFT_WRIST, Joint.LEFT_INDEX, Joint.LEFT_PINKY, Joint.LEFT_THUMB).forEach { set(it, lh.x, lh.y) }
        listOf(Joint.RIGHT_WRIST, Joint.RIGHT_INDEX, Joint.RIGHT_PINKY, Joint.RIGHT_THUMB).forEach { set(it, rh.x, rh.y) }
        // Legs
        set(Joint.LEFT_HIP, centerX - 0.05f, hipY); set(Joint.RIGHT_HIP, centerX + 0.05f, hipY)
        set(Joint.LEFT_KNEE, centerX - 0.05f, hipY + 0.12f); set(Joint.RIGHT_KNEE, centerX + 0.05f, hipY + 0.12f)
        listOf(Joint.LEFT_ANKLE, Joint.LEFT_HEEL, Joint.LEFT_FOOT_INDEX).forEach { set(it, centerX - 0.05f, hipY + 0.24f) }
        listOf(Joint.RIGHT_ANKLE, Joint.RIGHT_HEEL, Joint.RIGHT_FOOT_INDEX).forEach { set(it, centerX + 0.05f, hipY + 0.24f) }

        if (noise > 0f) {
            var s = seed * 7919 + 1
            fun rnd(): Float { s = (s * 1103515245 + 12345) and 0x7fffffff; return (s % 2001) / 1000f - 1f }
            for (i in 0 until Joint.COUNT) { x[i] += rnd() * noise; y[i] += rnd() * noise }
        }
        return Pose(x, y, z, v)
    }
}

/** Feeds frames at ~30 Hz and records everything that comes out. */
class Sim(config: MotionConfig = MotionConfig()) {
    val processor = MotionProcessor(config)
    var timeMs = 1_000L
    val events = mutableListOf<MotionEvent>()
    lateinit var last: MotionSnapshot

    fun frame(body: SyntheticBody?, count: Int = 1): MotionSnapshot {
        repeat(count) {
            timeMs += 33
            last = processor.process(MotionFrame(timeMs, 0.75f, body?.pose()))
            events += last.events
        }
        return last
    }

    fun calibrate(body: SyntheticBody = SyntheticBody()) {
        frame(body, 60)
        check(last.tracking == TrackingState.Ready) { "Expected Ready after calibration, got ${last.tracking}" }
        events.clear()
    }

    inline fun <reified T : MotionEvent> count() = events.count { it is T }
}
