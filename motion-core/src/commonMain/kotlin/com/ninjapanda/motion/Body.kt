package com.ninjapanda.motion

/**
 * Key body measurements for one frame, in *aspect units*: x is scaled by the frame's
 * aspect ratio so x and y distances are comparable (y runs 0..1 down the frame).
 */
internal data class BodyMeasure(
    val shoulderCenter: Vec2,
    val hipCenter: Vec2,
    /** Shoulder-centre to hip-centre distance. Our unit of body size. */
    val torso: Float,
) {
    /** Sideways body position: halfway between shoulders and hips, so both stepping and leaning count. */
    val bodyX: Float get() = (shoulderCenter.x + hipCenter.x) / 2f

    companion object {
        fun from(pose: Pose, aspect: Float, visibilityThreshold: Float): BodyMeasure? {
            val keyJoints = arrayOf(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP)
            if (keyJoints.any { !pose.isVisible(it, visibilityThreshold) }) return null
            val shoulders = Vec2.mid(pose.toAspect(Joint.LEFT_SHOULDER, aspect), pose.toAspect(Joint.RIGHT_SHOULDER, aspect))
            val hips = Vec2.mid(pose.toAspect(Joint.LEFT_HIP, aspect), pose.toAspect(Joint.RIGHT_HIP, aspect))
            val torso = shoulders.distanceTo(hips)
            if (torso <= 1e-4f) return null
            return BodyMeasure(shoulders, hips, torso)
        }
    }
}

internal fun Pose.toAspect(joint: Joint, aspect: Float): Vec2 = Vec2(x[joint.ordinal] * aspect, y[joint.ordinal])

/** The player's standing pose, recorded at calibration and slowly adapted afterwards. */
internal class Baseline(
    var shoulderCenter: Vec2,
    var hipCenter: Vec2,
    var torso: Float,
) {
    val bodyX: Float get() = (shoulderCenter.x + hipCenter.x) / 2f
}

/** Waits for the player to stand still in view, then averages their pose into a [Baseline]. */
internal class Calibrator(private val config: MotionConfig) {
    private var startMs = -1L
    private var count = 0
    private var shoulderSum = Vec2.Zero
    private var hipSum = Vec2.Zero
    private var torsoSum = 0f
    private var last: BodyMeasure? = null
    private var lastMs = 0L

    var progress = 0f
        private set

    fun reset() {
        startMs = -1L
        count = 0
        shoulderSum = Vec2.Zero
        hipSum = Vec2.Zero
        torsoSum = 0f
        last = null
        progress = 0f
    }

    /** Returns the baseline once the player has held still long enough, else null. */
    fun update(m: BodyMeasure, timeMs: Long): Baseline? {
        val previous = last
        if (previous != null) {
            val dt = (timeMs - lastMs).coerceAtLeast(1L) / 1000f
            val speed = previous.hipCenter.distanceTo(m.hipCenter) / m.torso / dt
            if (speed > config.calibrationMaxSpeed) {
                reset()
            }
        }
        last = m
        lastMs = timeMs

        if (startMs < 0) startMs = timeMs
        count++
        shoulderSum += m.shoulderCenter
        hipSum += m.hipCenter
        torsoSum += m.torso
        progress = ((timeMs - startMs).toFloat() / config.calibrationDurationMs).coerceIn(0f, 1f)

        if (progress < 1f) return null
        val baseline = Baseline(shoulderSum / count.toFloat(), hipSum / count.toFloat(), torsoSum / count)
        reset()
        progress = 1f
        return baseline
    }
}
