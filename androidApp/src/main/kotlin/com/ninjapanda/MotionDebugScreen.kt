package com.ninjapanda

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninjapanda.motion.MotionEngine
import com.ninjapanda.motion.MotionEvent
import com.ninjapanda.motion.camera.MotionCamera
import com.ninjapanda.motion.camera.PoseLandmarkerSource
import com.ninjapanda.motion.debug.MotionHud
import com.ninjapanda.motion.debug.SkeletonOverlay
import com.ninjapanda.motion.debug.describe
import kotlinx.coroutines.delay

@Composable
fun MotionDebugApp(cameraGranted: Boolean, onRequestCamera: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (cameraGranted) {
                MotionDebugScreen()
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("The camera is used to see your moves. Nothing is recorded or saved.")
                    Button(onClick = onRequestCamera, modifier = Modifier.padding(top = 16.dp)) { Text("Allow camera") }
                }
            }
        }
    }
}

@Composable
private fun MotionDebugScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val engine = remember { MotionEngine() }
    val source = remember { PoseLandmarkerSource(context, onFrame = engine::submit) }
    val camera = remember { MotionCamera(context, source) }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        camera.bind(lifecycleOwner, previewView.surfaceProvider)
        onDispose {
            camera.shutdown()
            source.close()
        }
    }

    val snapshot by engine.snapshot.collectAsStateWithLifecycle()
    val recentEvents = remember { mutableStateListOf<MotionEvent>() }
    var flash by remember { mutableStateOf<MotionEvent?>(null) }
    LaunchedEffect(engine) {
        engine.events.collect { event ->
            recentEvents.add(event)
            if (recentEvents.size > 20) recentEvents.removeAt(0)
            if (event !is MotionEvent.DuckEnd) flash = event
        }
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(700)
            flash = null
        }
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Camera is 4:3 landscape sensor → 3:4 upright in portrait.
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            SkeletonOverlay(snapshot, Modifier.fillMaxSize())
            flash?.let {
                Text(
                    describe(it),
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
                Button(onClick = engine::recalibrate) { Text("Recalibrate") }
            }
            MotionHud(snapshot, recentEvents, source.accelerator.name)
        }
    }
}
