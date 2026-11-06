package com.ninjapanda.motion.link

import kotlin.time.TimeSource

/** Default monotonic millisecond clock. On Android, pass SystemClock::uptimeMillis instead so it matches camera timestamps. */
internal object MonotonicClock {
    private val start = TimeSource.Monotonic.markNow()
    fun now(): Long = start.elapsedNow().inWholeMilliseconds
}
