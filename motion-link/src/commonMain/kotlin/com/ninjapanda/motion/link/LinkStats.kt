package com.ninjapanda.motion.link

/**
 * Estimates the offset between the phone's clock and the TV's clock from ping/pong
 * round trips (like NTP): the sample with the smallest round-trip time is the most accurate.
 */
class ClockSync(private val window: Int = 20) {
    private class Sample(val offsetMs: Double, val rttMs: Long)
    private val samples = ArrayDeque<Sample>()

    /** phoneClock ≈ tvClock + offset. Null until the first pong. */
    var offsetMs: Double? = null
        private set
    var rttMs: Long? = null
        private set

    fun addSample(tvSendMs: Long, phoneTimeMs: Long, tvReceiveMs: Long) {
        val rtt = tvReceiveMs - tvSendMs
        if (rtt < 0) return
        samples.addLast(Sample(phoneTimeMs - (tvSendMs + rtt / 2.0), rtt))
        while (samples.size > window) samples.removeFirst()
        val best = samples.minBy { it.rttMs }
        offsetMs = best.offsetMs
        rttMs = samples.last().rttMs
    }

    fun reset() {
        samples.clear()
        offsetMs = null
        rttMs = null
    }
}

/** Live link quality, shown on the TV. Lag is camera-frame-on-phone → packet-on-TV. */
data class LinkStats(
    val packetsPerSecond: Float = 0f,
    val lossPercent: Float = 0f,
    val lateDropped: Long = 0,
    val rejected: Long = 0,
    val lagAvgMs: Float? = null,
    val lagP95Ms: Float? = null,
    val lagMaxMs: Float? = null,
    val jitterMs: Float? = null,
    val rttMs: Long? = null,
    val phoneLatencyMs: Int = 0,
    val totalReceived: Long = 0,
)

internal class LinkStatsTracker(private val windowMs: Long = 2000) {
    private class Entry(val timeMs: Long, val sequence: Long, val lagMs: Float?)
    private val entries = ArrayDeque<Entry>()
    var lateDropped = 0L
    var rejected = 0L
    var totalReceived = 0L
    var lastPhoneLatency = 0

    fun onFrame(nowMs: Long, sequence: Long, lagMs: Float?, phoneLatencyMs: Int) {
        entries.addLast(Entry(nowMs, sequence, lagMs))
        totalReceived++
        lastPhoneLatency = phoneLatencyMs
        trim(nowMs)
    }

    fun reset() {
        entries.clear(); lateDropped = 0; rejected = 0; totalReceived = 0; lastPhoneLatency = 0
    }

    fun snapshot(nowMs: Long, rttMs: Long?): LinkStats {
        trim(nowMs)
        if (entries.isEmpty()) return LinkStats(lateDropped = lateDropped, rejected = rejected, rttMs = rttMs, totalReceived = totalReceived)
        val span = (nowMs - entries.first().timeMs).coerceAtLeast(1)
        val pps = entries.size * 1000f / maxOf(span, 500L)
        val expected = entries.maxOf { it.sequence } - entries.minOf { it.sequence } + 1
        val loss = if (expected > 0) (1f - entries.size.toFloat() / expected).coerceIn(0f, 1f) * 100f else 0f
        val lags = entries.mapNotNull { it.lagMs }.sorted()
        val jitter = if (lags.size > 1) {
            var sum = 0f
            val ordered = entries.mapNotNull { it.lagMs }
            for (i in 1 until ordered.size) sum += kotlin.math.abs(ordered[i] - ordered[i - 1])
            sum / (ordered.size - 1)
        } else null
        return LinkStats(
            packetsPerSecond = pps,
            lossPercent = loss,
            lateDropped = lateDropped,
            rejected = rejected,
            lagAvgMs = lags.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            lagP95Ms = lags.takeIf { it.isNotEmpty() }?.let { it[((it.size - 1) * 0.95f).toInt()] },
            lagMaxMs = lags.lastOrNull(),
            jitterMs = jitter,
            rttMs = rttMs,
            phoneLatencyMs = lastPhoneLatency,
            totalReceived = totalReceived,
        )
    }

    private fun trim(nowMs: Long) {
        while (entries.isNotEmpty() && nowMs - entries.first().timeMs > windowMs) entries.removeFirst()
    }
}
