package com.ninjapanda.motion

import kotlin.math.abs

/**
 * Whole-body signals compared with the calibrated standing [Baseline]:
 * lean and lanes (sideways), jump (hips rise), duck/squat/sit (shoulders drop).
 * Every signal has separate enter/exit thresholds so it doesn't flicker at the edge.
 */
internal class BodyDetector(private val config: MotionConfig) {
    var lane = Lane.Center
        private set
    var posture = Posture.Standing
        private set
    var lean = 0f
        private set
    private var lastJumpMs = Long.MIN_VALUE / 2

    fun reset() {
        lane = Lane.Center
        posture = Posture.Standing
        lean = 0f
    }

    fun update(m: BodyMeasure, b: Baseline, timeMs: Long, dtSeconds: Float, events: MutableList<MotionEvent>) {
        // Sideways
        val offset = (m.bodyX - b.bodyX) / b.torso
        lean = (offset / config.leanFullOffset).coerceIn(-1f, 1f)
        val newLane = when {
            offset < -config.laneEnter -> Lane.Left
            offset > config.laneEnter -> Lane.Right
            abs(offset) < config.laneExit -> Lane.Center
            else -> lane
        }
        if (newLane != lane) {
            events += MotionEvent.LaneChanged(lane, newLane, timeMs)
            lane = newLane
        }

        // Vertical (y grows downwards)
        val rise = (b.hipCenter.y - m.hipCenter.y) / b.torso
        val drop = (m.shoulderCenter.y - b.shoulderCenter.y) / b.torso
        when (posture) {
            Posture.Standing -> when {
                rise > config.jumpEnter && timeMs - lastJumpMs >= config.jumpCooldownMs -> {
                    posture = Posture.Jumping
                    lastJumpMs = timeMs
                    events += MotionEvent.Jump(timeMs)
                }
                drop > config.duckEnter -> {
                    posture = Posture.Ducking
                    events += MotionEvent.DuckStart(timeMs)
                }
            }
            Posture.Jumping -> if (rise < config.jumpExit) posture = Posture.Standing
            Posture.Ducking -> if (drop < config.duckExit) {
                posture = Posture.Standing
                events += MotionEvent.DuckEnd(timeMs)
            }
        }

        adaptBaseline(m, b, offset, rise, drop, dtSeconds)
    }

    /** Let the baseline follow slow drift (player shuffling around) while they stand neutrally. */
    private fun adaptBaseline(m: BodyMeasure, b: Baseline, offset: Float, rise: Float, drop: Float, dt: Float) {
        if (posture != Posture.Standing || abs(rise) > config.jumpExit || abs(drop) > config.duckExit) return
        val k = (dt / config.baselineAdaptSeconds).coerceIn(0f, 1f)
        b.hipCenter = Vec2(b.hipCenter.x, b.hipCenter.y + (m.hipCenter.y - b.hipCenter.y) * k)
        b.shoulderCenter = Vec2(b.shoulderCenter.x, b.shoulderCenter.y + (m.shoulderCenter.y - b.shoulderCenter.y) * k)
        b.torso += (m.torso - b.torso) * k
        if (lane == Lane.Center && abs(offset) < config.laneExit) {
            val dx = (m.bodyX - b.bodyX) * k * 0.5f
            b.hipCenter = Vec2(b.hipCenter.x + dx, b.hipCenter.y)
            b.shoulderCenter = Vec2(b.shoulderCenter.x + dx, b.shoulderCenter.y)
        }
    }
}
