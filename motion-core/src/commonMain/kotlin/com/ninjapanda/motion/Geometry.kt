package com.ninjapanda.motion

import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec2(x * s, y * s)
    operator fun div(s: Float) = Vec2(x / s, y / s)
    fun length(): Float = sqrt(x * x + y * y)
    fun distanceTo(o: Vec2): Float = (this - o).length()

    companion object {
        val Zero = Vec2(0f, 0f)
        fun mid(a: Vec2, b: Vec2) = Vec2((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
}

/** Axis-aligned rectangle. */
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
