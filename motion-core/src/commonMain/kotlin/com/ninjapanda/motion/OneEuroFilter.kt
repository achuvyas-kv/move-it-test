package com.ninjapanda.motion

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter (Casiez et al., 2012): strong smoothing while a value is still,
 * little lag while it moves fast. Ideal for landmarks: removes jitter at rest
 * without making fast swings feel late.
 *
 * @param minCutoff Hz; lower = smoother at rest.
 * @param beta how quickly the cutoff rises with speed; higher = less lag on fast moves.
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.0f,
    private val derivativeCutoff: Float = 1.0f,
) {
    private var initialised = false
    private var previous = 0f
    private var previousDerivative = 0f
    private var previousTimeMs = 0L

    fun filter(value: Float, timeMs: Long): Float {
        if (!initialised) {
            initialised = true
            previous = value
            previousDerivative = 0f
            previousTimeMs = timeMs
            return value
        }
        val dt = (timeMs - previousTimeMs).coerceAtLeast(1L) / 1000f
        previousTimeMs = timeMs

        val derivative = (value - previous) / dt
        val smoothedDerivative = lerp(previousDerivative, derivative, alpha(dt, derivativeCutoff))
        previousDerivative = smoothedDerivative

        val cutoff = minCutoff + beta * abs(smoothedDerivative)
        val smoothed = lerp(previous, value, alpha(dt, cutoff))
        previous = smoothed
        return smoothed
    }

    fun reset() {
        initialised = false
    }

    private fun alpha(dt: Float, cutoff: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
