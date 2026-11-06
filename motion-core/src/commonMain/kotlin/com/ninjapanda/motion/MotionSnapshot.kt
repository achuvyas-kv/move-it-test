package com.ninjapanda.motion

/** Can the pipeline see the player well enough to play? */
enum class TrackingState {
    /** Nobody in front of the camera. */
    NoPerson,
    /** Shoulders or hips are out of frame or hidden. */
    NotFullyVisible,
    TooClose,
    TooFar,
    /** Player is in view; standing still to record their baseline. */
    Calibrating,
    Ready,
}

enum class HandSide { Left, Right }

enum class Lane { Left, Center, Right }

enum class Posture { Standing, Jumping, Ducking }

data class TrailPoint(val position: Vec2, val timestampMs: Long)

/**
 * One hand, as a game cursor.
 *
 * @param cursor position in the player's reach box, 0..1 on both axes (map it to your screen).
 * @param velocity cursor units per second.
 * @param bladeActive true while the hand is moving fast enough to count as a slash.
 * @param trail recent cursor positions, newest last (for drawing blade trails).
 */
data class HandState(
    val side: HandSide,
    val visible: Boolean,
    val cursor: Vec2,
    val velocity: Vec2,
    val speed: Float,
    val bladeActive: Boolean,
    val trail: List<TrailPoint>,
) {
    companion object {
        fun hidden(side: HandSide) = HandState(side, false, Vec2(0.5f, 0.5f), Vec2.Zero, 0f, false, emptyList())
    }
}

/** Discrete things that happened, for games to react to. */
sealed interface MotionEvent {
    val timestampMs: Long

    data class Calibrated(override val timestampMs: Long) : MotionEvent
    data class PlayerLost(override val timestampMs: Long) : MotionEvent

    /** A completed fast swipe. Points are in reach-box (cursor) space. */
    data class Slash(
        val side: HandSide,
        val path: List<Vec2>,
        val peakSpeed: Float,
        override val timestampMs: Long,
    ) : MotionEvent {
        val start: Vec2 get() = path.first()
        val end: Vec2 get() = path.last()
    }

    data class Jump(override val timestampMs: Long) : MotionEvent
    data class DuckStart(override val timestampMs: Long) : MotionEvent
    data class DuckEnd(override val timestampMs: Long) : MotionEvent
    data class LaneChanged(val from: Lane, val to: Lane, override val timestampMs: Long) : MotionEvent
}

/**
 * Everything a game needs about the player for one frame. Games read this; they
 * should never need raw landmarks.
 */
data class MotionSnapshot(
    val timestampMs: Long,
    val tracking: TrackingState,
    /** 0..1 while [TrackingState.Calibrating]. */
    val calibrationProgress: Float,
    /** Smoothed pose in frame coordinates (for debug drawing), or null. */
    val pose: Pose?,
    val aspectRatio: Float,
    /** The reach box in frame coordinates (0..1), for mapping cursors back onto the camera image. */
    val reachBox: Rect?,
    val leftHand: HandState,
    val rightHand: HandState,
    /** -1 (player leaning/stepped to their left) .. +1 (right). Mirrored, so it matches the screen. */
    val lean: Float,
    val lane: Lane,
    val posture: Posture,
    /** Events produced by this frame. Also streamed by [MotionEngine.events]. */
    val events: List<MotionEvent>,
    val poseRateHz: Float,
    val latencyMs: Float,
) {
    fun hand(side: HandSide) = if (side == HandSide.Left) leftHand else rightHand

    /** Map a cursor-space point back to frame coordinates (for overlays on the camera image). */
    fun cursorToFrame(p: Vec2): Vec2? = reachBox?.let { Vec2(it.left + p.x * it.width, it.top + p.y * it.height) }

    companion object {
        val Empty = MotionSnapshot(
            timestampMs = 0, tracking = TrackingState.NoPerson, calibrationProgress = 0f,
            pose = null, aspectRatio = 1f, reachBox = null,
            leftHand = HandState.hidden(HandSide.Left), rightHand = HandState.hidden(HandSide.Right),
            lean = 0f, lane = Lane.Center, posture = Posture.Standing, events = emptyList(),
            poseRateHz = 0f, latencyMs = 0f,
        )
    }
}
