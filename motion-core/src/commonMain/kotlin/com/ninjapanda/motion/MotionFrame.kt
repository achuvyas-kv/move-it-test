package com.ninjapanda.motion

/**
 * One person's 33 landmarks.
 *
 * Coordinates are normalised to the camera frame as the player sees it on screen
 * (upright, and mirrored for the front camera): x and y run 0..1 from the top-left,
 * y grows downwards. [z] is the model's relative depth (smaller = closer).
 * [visibility] is the model's 0..1 confidence that the joint is visible.
 */
class Pose(
    val x: FloatArray,
    val y: FloatArray,
    val z: FloatArray,
    val visibility: FloatArray,
) {
    init {
        require(x.size == Joint.COUNT && y.size == Joint.COUNT && z.size == Joint.COUNT && visibility.size == Joint.COUNT) {
            "A pose needs exactly ${Joint.COUNT} landmarks"
        }
    }

    operator fun get(joint: Joint): Vec2 = Vec2(x[joint.ordinal], y[joint.ordinal])
    fun visibility(joint: Joint): Float = visibility[joint.ordinal]
    fun isVisible(joint: Joint, threshold: Float = 0.5f): Boolean = visibility[joint.ordinal] >= threshold

    fun copy(): Pose = Pose(x.copyOf(), y.copyOf(), z.copyOf(), visibility.copyOf())
}

/**
 * Output of a platform pose source for one camera frame.
 *
 * @param timestampMs monotonic capture time of the camera frame.
 * @param aspectRatio frame width / height, so distances can be measured in true proportions.
 * @param pose the detected player, or null when nobody was found.
 * @param latencyMs time from camera frame to pose result (for the debug overlay).
 */
class MotionFrame(
    val timestampMs: Long,
    val aspectRatio: Float,
    val pose: Pose?,
    val latencyMs: Float = 0f,
)
