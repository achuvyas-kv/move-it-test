package com.ninjapanda.motion

import kotlin.math.max

/**
 * Turns one hand into a game cursor and detects fast swipes ("slashes").
 * Works in reach-box space, so a short child and a tall adult both cover the whole screen.
 */
internal class HandTracker(private val side: HandSide, private val config: MotionConfig) {
    private val wrist = if (side == HandSide.Left) Joint.LEFT_WRIST else Joint.RIGHT_WRIST
    private val index = if (side == HandSide.Left) Joint.LEFT_INDEX else Joint.RIGHT_INDEX

    private var previous: Vec2? = null
    private var previousMs = 0L
    private var velocity = Vec2.Zero
    private var slashing = false
    private val slashPath = ArrayList<Vec2>()
    private var slashPeak = 0f
    private val trail = ArrayDeque<TrailPoint>()

    fun reset() {
        previous = null
        velocity = Vec2.Zero
        slashing = false
        slashPath.clear()
        trail.clear()
    }

    /** @param box the reach box in aspect units. */
    fun update(pose: Pose, aspect: Float, box: Rect, timeMs: Long, events: MutableList<MotionEvent>): HandState {
        if (!pose.isVisible(wrist, config.visibilityThreshold)) {
            finishSlash(timeMs, events)
            reset()
            return HandState.hidden(side)
        }

        val wristPoint = pose.toAspect(wrist, aspect)
        val handPoint = if (pose.isVisible(index, config.visibilityThreshold)) {
            Vec2.mid(wristPoint, pose.toAspect(index, aspect))
        } else {
            wristPoint
        }
        // Unclamped position in the reach box; clamped only for the cursor.
        val raw = Vec2((handPoint.x - box.left) / box.width, (handPoint.y - box.top) / box.height)

        val prev = previous
        if (prev != null) {
            val dt = (timeMs - previousMs).coerceAtLeast(1L) / 1000f
            val instant = (raw - prev) / dt
            velocity = velocity * 0.4f + instant * 0.6f
        }
        previous = raw
        previousMs = timeMs
        val speed = velocity.length()

        if (!slashing && speed > config.slashStartSpeed) {
            slashing = true
            slashPath.clear()
            slashPath.add(prev ?: raw)
            slashPath.add(raw)
            slashPeak = speed
        } else if (slashing) {
            slashPath.add(raw)
            slashPeak = max(slashPeak, speed)
            if (speed < config.slashEndSpeed) finishSlash(timeMs, events)
        }

        val cursor = Vec2(raw.x.coerceIn(0f, 1f), raw.y.coerceIn(0f, 1f))
        trail.addLast(TrailPoint(cursor, timeMs))
        while (trail.isNotEmpty() && timeMs - trail.first().timestampMs > config.trailDurationMs) trail.removeFirst()

        return HandState(side, true, cursor, velocity, speed, slashing, trail.toList())
    }

    private fun finishSlash(timeMs: Long, events: MutableList<MotionEvent>) {
        if (!slashing) return
        slashing = false
        var length = 0f
        for (i in 1 until slashPath.size) length += slashPath[i - 1].distanceTo(slashPath[i])
        if (length >= config.slashMinLength) {
            events += MotionEvent.Slash(side, slashPath.toList(), slashPeak, timeMs)
        }
        slashPath.clear()
    }
}
