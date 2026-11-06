package com.ninjapanda.motion

/**
 * The whole motion pipeline, synchronous and platform-free:
 * smooth → measure → calibrate → hands + body detectors → [MotionSnapshot].
 *
 * Not thread-safe: feed frames from one thread (or use [MotionEngine]).
 */
class MotionProcessor(private val config: MotionConfig = MotionConfig()) {
    private val xFilters = Array(Joint.COUNT) { OneEuroFilter(config.smoothingMinCutoff, config.smoothingBeta) }
    private val yFilters = Array(Joint.COUNT) { OneEuroFilter(config.smoothingMinCutoff, config.smoothingBeta) }
    private val calibrator = Calibrator(config)
    private val leftHand = HandTracker(HandSide.Left, config)
    private val rightHand = HandTracker(HandSide.Right, config)
    private val body = BodyDetector(config)

    private var baseline: Baseline? = null
    private var reachCenter: Vec2? = null
    private var lastFrameMs = -1L
    private var lastPoseMs = -1L
    private var poseRateHz = 0f

    /** Forget the player's baseline; they will be asked to stand still again. */
    fun recalibrate() {
        baseline = null
        calibrator.reset()
        body.reset()
    }

    fun process(frame: MotionFrame): MotionSnapshot {
        val t = frame.timestampMs
        val events = ArrayList<MotionEvent>(2)
        val dtSeconds = if (lastFrameMs >= 0) (t - lastFrameMs).coerceAtLeast(1L) / 1000f else 0f
        if (dtSeconds > 0f) poseRateHz = if (poseRateHz == 0f) 1f / dtSeconds else poseRateHz * 0.9f + (1f / dtSeconds) * 0.1f
        lastFrameMs = t

        val rawPose = frame.pose
        if (rawPose == null) {
            if (baseline != null && lastPoseMs >= 0 && t - lastPoseMs > config.lostTimeoutMs) {
                recalibrate()
                events += MotionEvent.PlayerLost(t)
            }
            leftHand.reset()
            rightHand.reset()
            return snapshot(frame, TrackingState.NoPerson, null, null, hidden = true, events = events)
        }

        if (lastPoseMs >= 0 && t - lastPoseMs > 300) resetFilters()
        lastPoseMs = t
        val pose = smooth(rawPose, t)
        val aspect = frame.aspectRatio

        val measure = BodyMeasure.from(pose, aspect, config.visibilityThreshold)
        if (measure == null) {
            leftHand.reset()
            rightHand.reset()
            calibrator.reset()
            return snapshot(frame, TrackingState.NotFullyVisible, pose, null, hidden = true, events = events)
        }

        val distance = when {
            measure.torso > config.tooCloseTorso -> TrackingState.TooClose
            measure.torso < config.tooFarTorso -> TrackingState.TooFar
            else -> null
        }

        var tracking: TrackingState
        val currentBaseline = baseline
        if (currentBaseline == null) {
            if (distance != null) {
                calibrator.reset()
                tracking = distance
            } else {
                val result = calibrator.update(measure, t)
                if (result != null) {
                    baseline = result
                    events += MotionEvent.Calibrated(t)
                    tracking = TrackingState.Ready
                } else {
                    tracking = TrackingState.Calibrating
                }
            }
        } else {
            tracking = distance ?: TrackingState.Ready
        }

        // Reach box follows the shoulders with a little lag, so body sway doesn't jitter the cursor.
        val torso = baseline?.torso ?: measure.torso
        val center = reachCenter?.let { it + (measure.shoulderCenter - it) * 0.25f } ?: measure.shoulderCenter
        reachCenter = center
        val box = Rect(
            left = center.x - config.reachHalfWidth * torso,
            top = center.y - config.reachAbove * torso,
            right = center.x + config.reachHalfWidth * torso,
            bottom = center.y + config.reachBelow * torso,
        )
        val left = leftHand.update(pose, aspect, box, t, events)
        val right = rightHand.update(pose, aspect, box, t, events)

        baseline?.let { body.update(measure, it, t, dtSeconds, events) }

        val frameBox = Rect(box.left / aspect, box.top, box.right / aspect, box.bottom)
        return MotionSnapshot(
            timestampMs = t,
            tracking = tracking,
            calibrationProgress = if (baseline == null) calibrator.progress else 1f,
            pose = pose,
            aspectRatio = aspect,
            reachBox = frameBox,
            leftHand = left,
            rightHand = right,
            lean = body.lean,
            lane = body.lane,
            posture = body.posture,
            events = events,
            poseRateHz = poseRateHz,
            latencyMs = frame.latencyMs,
        )
    }

    private fun smooth(raw: Pose, t: Long): Pose {
        val out = raw.copy()
        for (i in 0 until Joint.COUNT) {
            out.x[i] = xFilters[i].filter(raw.x[i], t)
            out.y[i] = yFilters[i].filter(raw.y[i], t)
        }
        return out
    }

    private fun resetFilters() {
        xFilters.forEach { it.reset() }
        yFilters.forEach { it.reset() }
        reachCenter = null
    }

    private fun snapshot(
        frame: MotionFrame,
        tracking: TrackingState,
        pose: Pose?,
        box: Rect?,
        hidden: Boolean,
        events: List<MotionEvent>,
    ) = MotionSnapshot(
        timestampMs = frame.timestampMs,
        tracking = tracking,
        calibrationProgress = if (baseline == null) calibrator.progress else 1f,
        pose = pose,
        aspectRatio = frame.aspectRatio,
        reachBox = box,
        leftHand = HandState.hidden(HandSide.Left),
        rightHand = HandState.hidden(HandSide.Right),
        lean = if (hidden) 0f else body.lean,
        lane = body.lane,
        posture = body.posture,
        events = events,
        poseRateHz = poseRateHz,
        latencyMs = frame.latencyMs,
    )
}
