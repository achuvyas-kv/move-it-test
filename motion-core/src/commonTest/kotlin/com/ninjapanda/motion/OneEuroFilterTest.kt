package com.ninjapanda.motion

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OneEuroFilterTest {
    @Test
    fun reducesJitterAtRest() {
        val f = OneEuroFilter(minCutoff = 1.2f, beta = 8f)
        var maxDev = 0f
        for (i in 0 until 100) {
            val noisy = 0.5f + if (i % 2 == 0) 0.005f else -0.005f
            val out = f.filter(noisy, i * 33L)
            if (i > 10) maxDev = maxOf(maxDev, abs(out - 0.5f))
        }
        assertTrue(maxDev < 0.003f, "jitter should be damped, got $maxDev")
    }

    @Test
    fun followsFastMovesQuickly() {
        val f = OneEuroFilter(minCutoff = 1.2f, beta = 8f)
        f.filter(0f, 0)
        var out = 0f
        for (i in 1..5) out = f.filter(i * 0.1f, i * 33L)
        assertTrue(out > 0.35f, "should track a fast move with little lag, got $out")
    }
}
