package com.ninjapanda

import android.content.Context
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ninjapanda.motion.MotionFrame
import com.ninjapanda.motion.camera.MotionCamera
import com.ninjapanda.motion.camera.PoseLandmarkerSource

/** Front camera + MediaPipe pose, delivering [MotionFrame]s to a callback. */
class PoseCameraController(context: Context, onFrame: (MotionFrame) -> Unit) {
    val source = PoseLandmarkerSource(context, onFrame = onFrame)
    val camera = MotionCamera(context, source)
    val previewView = PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }

    fun close() {
        camera.shutdown()
        source.close()
    }
}

/** Starts the camera while this composable is on screen. [onFrame] runs on MediaPipe's thread. */
@Composable
fun rememberPoseCamera(onFrame: (MotionFrame) -> Unit): PoseCameraController {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnFrame by rememberUpdatedState(onFrame)
    val controller = remember { PoseCameraController(context) { currentOnFrame(it) } }
    DisposableEffect(lifecycleOwner) {
        controller.camera.bind(lifecycleOwner, controller.previewView.surfaceProvider)
        onDispose { controller.close() }
    }
    return controller
}

@Composable
fun CameraPreview(controller: PoseCameraController, modifier: Modifier = Modifier) {
    AndroidView(factory = { controller.previewView }, modifier = modifier)
}
