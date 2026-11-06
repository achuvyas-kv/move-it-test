package com.ninjapanda.motion

/**
 * The 33 BlazePose landmarks. Ordinals match the model's output indices.
 * LEFT/RIGHT are the *player's* left/right.
 */
enum class Joint {
    NOSE,
    LEFT_EYE_INNER, LEFT_EYE, LEFT_EYE_OUTER,
    RIGHT_EYE_INNER, RIGHT_EYE, RIGHT_EYE_OUTER,
    LEFT_EAR, RIGHT_EAR,
    MOUTH_LEFT, MOUTH_RIGHT,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST,
    LEFT_PINKY, RIGHT_PINKY,
    LEFT_INDEX, RIGHT_INDEX,
    LEFT_THUMB, RIGHT_THUMB,
    LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE,
    LEFT_ANKLE, RIGHT_ANKLE,
    LEFT_HEEL, RIGHT_HEEL,
    LEFT_FOOT_INDEX, RIGHT_FOOT_INDEX;

    companion object {
        const val COUNT = 33

        /** Pairs of joints to connect when drawing a skeleton. */
        val BONES: List<Pair<Joint, Joint>> = listOf(
            LEFT_SHOULDER to RIGHT_SHOULDER,
            LEFT_HIP to RIGHT_HIP,
            LEFT_SHOULDER to LEFT_HIP, RIGHT_SHOULDER to RIGHT_HIP,
            LEFT_SHOULDER to LEFT_ELBOW, LEFT_ELBOW to LEFT_WRIST,
            RIGHT_SHOULDER to RIGHT_ELBOW, RIGHT_ELBOW to RIGHT_WRIST,
            LEFT_WRIST to LEFT_INDEX, RIGHT_WRIST to RIGHT_INDEX,
            LEFT_HIP to LEFT_KNEE, LEFT_KNEE to LEFT_ANKLE, LEFT_ANKLE to LEFT_FOOT_INDEX,
            RIGHT_HIP to RIGHT_KNEE, RIGHT_KNEE to RIGHT_ANKLE, RIGHT_ANKLE to RIGHT_FOOT_INDEX,
            LEFT_EAR to LEFT_EYE, LEFT_EYE to NOSE, NOSE to RIGHT_EYE, RIGHT_EYE to RIGHT_EAR,
        )
    }
}
