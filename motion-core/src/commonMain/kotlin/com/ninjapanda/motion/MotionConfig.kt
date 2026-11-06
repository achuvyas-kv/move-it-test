package com.ninjapanda.motion

/**
 * Every tunable number in the motion pipeline, in one place.
 *
 * Body distances are in **torso lengths** (shoulder-centre to hip-centre, measured at
 * calibration), so the same values work for a small child far away and a tall adult close up.
 * Hand speeds are in **reach-box units per second** (1.0 = the full width of the player's reach).
 */
data class MotionConfig(
    // Smoothing (One Euro filter on every landmark coordinate)
    val smoothingMinCutoff: Float = 1.2f,
    val smoothingBeta: Float = 8f,
    val visibilityThreshold: Float = 0.5f,

    // Tracking
    /** No pose for this long → the player is considered gone and must recalibrate. */
    val lostTimeoutMs: Long = 1500,
    /** Torso longer than this fraction of the frame height → "step back". */
    val tooCloseTorso: Float = 0.42f,
    /** Torso shorter than this fraction of the frame height → "come closer". */
    val tooFarTorso: Float = 0.07f,

    // Calibration
    val calibrationDurationMs: Long = 1500,
    /** Max hip movement while calibrating, in torso lengths per second. */
    val calibrationMaxSpeed: Float = 0.6f,
    /** Seconds for the standing baseline to follow slow drift (player shuffling closer/further). */
    val baselineAdaptSeconds: Float = 4f,

    // Hands
    /** Reach box around the shoulder centre, in torso lengths. */
    val reachHalfWidth: Float = 1.5f,
    val reachAbove: Float = 1.4f,
    val reachBelow: Float = 0.9f,
    val slashStartSpeed: Float = 2.2f,
    val slashEndSpeed: Float = 1.0f,
    /** Shortest path (reach-box units) that counts as a slash. */
    val slashMinLength: Float = 0.2f,
    val trailDurationMs: Long = 180,

    // Body
    /** Sideways body offset to enter the left/right lane, and to return to centre. */
    val laneEnter: Float = 0.35f,
    val laneExit: Float = 0.2f,
    /** Body offset that maps to lean = ±1. */
    val leanFullOffset: Float = 0.6f,
    /** Hip rise that starts a jump, and the rise below which the jump has landed. */
    val jumpEnter: Float = 0.22f,
    val jumpExit: Float = 0.08f,
    val jumpCooldownMs: Long = 300,
    /** Shoulder drop that starts a duck/squat/sit, and the drop below which it ends. */
    val duckEnter: Float = 0.3f,
    val duckExit: Float = 0.15f,
)
