package com.ninjapanda.motion.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.ninjapanda.motion.Joint
import com.ninjapanda.motion.MotionFrame
import com.ninjapanda.motion.Pose
import java.io.Closeable

enum class Accelerator { GPU, CPU }

data class PoseSourceConfig(
    /** Model file in the app's assets: pose_landmarker_lite / _full / _heavy. */
    val modelAsset: String = "pose_landmarker_lite.task",
    val preferred: Accelerator = Accelerator.GPU,
    /** Mirror the image so the player's movements match the screen (front camera). */
    val mirror: Boolean = true,
    val minDetectionConfidence: Float = 0.5f,
    val minPresenceConfidence: Float = 0.5f,
    val minTrackingConfidence: Float = 0.5f,
)

/**
 * CameraX analyzer that runs MediaPipe Pose Landmarker (BlazePose) on each frame and
 * hands a [MotionFrame] to [onFrame] on MediaPipe's result thread.
 *
 * Tries the GPU delegate first and falls back to CPU if it can't be created.
 */
class PoseLandmarkerSource(
    context: Context,
    private val config: PoseSourceConfig = PoseSourceConfig(),
    private val onFrame: (MotionFrame) -> Unit,
) : ImageAnalysis.Analyzer, Closeable {

    private val appContext = context.applicationContext

    /** The accelerator actually in use. */
    val accelerator: Accelerator

    private val landmarker: PoseLandmarker
    private var lastTimestampMs = 0L

    @Volatile
    private var aspectRatio = 0.75f

    @Volatile
    private var closed = false

    init {
        val order = if (config.preferred == Accelerator.GPU) listOf(Accelerator.GPU, Accelerator.CPU) else listOf(Accelerator.CPU)
        var created: Pair<PoseLandmarker, Accelerator>? = null
        var lastError: Throwable? = null
        for (acc in order) {
            try {
                created = create(acc) to acc
                break
            } catch (t: Throwable) {
                Log.w(TAG, "Could not create PoseLandmarker on $acc", t)
                lastError = t
            }
        }
        val (lm, acc) = created ?: throw IllegalStateException("PoseLandmarker unavailable", lastError)
        landmarker = lm
        accelerator = acc
        Log.i(TAG, "PoseLandmarker running on $acc with ${config.modelAsset}")
    }

    private fun create(acc: Accelerator): PoseLandmarker {
        val base = BaseOptions.builder()
            .setModelAssetPath(config.modelAsset)
            .setDelegate(if (acc == Accelerator.GPU) Delegate.GPU else Delegate.CPU)
            .build()
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(config.minDetectionConfidence)
            .setMinPosePresenceConfidence(config.minPresenceConfidence)
            .setMinTrackingConfidence(config.minTrackingConfidence)
            .setResultListener(::onResult)
            .setErrorListener { e -> Log.e(TAG, "PoseLandmarker error", e) }
            .build()
        return PoseLandmarker.createFromOptions(appContext, options)
    }

    override fun analyze(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }
        // MediaPipe needs strictly increasing timestamps.
        var timestamp = SystemClock.uptimeMillis()
        if (timestamp <= lastTimestampMs) timestamp = lastTimestampMs + 1
        lastTimestampMs = timestamp

        val upright: Bitmap
        try {
            val bitmap = image.toBitmap()
            val matrix = Matrix().apply {
                postRotate(image.imageInfo.rotationDegrees.toFloat())
                if (config.mirror) postScale(-1f, 1f)
            }
            upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } finally {
            image.close()
        }
        aspectRatio = upright.width.toFloat() / upright.height
        landmarker.detectAsync(BitmapImageBuilder(upright).build(), timestamp)
    }

    private fun onResult(result: PoseLandmarkerResult, input: MPImage) {
        if (closed) return
        val latency = (SystemClock.uptimeMillis() - result.timestampMs()).toFloat()
        val person = result.landmarks().firstOrNull()
        val pose = person?.takeIf { it.size >= Joint.COUNT }?.let { lms ->
            val x = FloatArray(Joint.COUNT)
            val y = FloatArray(Joint.COUNT)
            val z = FloatArray(Joint.COUNT)
            val v = FloatArray(Joint.COUNT)
            for (i in 0 until Joint.COUNT) {
                val lm = lms[i]
                x[i] = lm.x()
                y[i] = lm.y()
                z[i] = lm.z()
                v[i] = lm.visibility().orElse(1f)
            }
            Pose(x, y, z, v)
        }
        onFrame(MotionFrame(result.timestampMs(), aspectRatio, pose, latency))
    }

    override fun close() {
        closed = true
        landmarker.close()
    }

    private companion object {
        const val TAG = "PoseLandmarkerSource"
    }
}
