package com.ninjapanda

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninjapanda.motion.MotionEngine
import com.ninjapanda.motion.debug.EventFlash
import com.ninjapanda.motion.debug.MotionHud
import com.ninjapanda.motion.debug.SkeletonOverlay
import com.ninjapanda.motion.debug.rememberEventFeed

/** Phone-only mode: camera, detection and readouts all on this phone. */
@Composable
fun MotionDebugScreen(onBack: () -> Unit) {
    val engine = remember { MotionEngine() }
    val camera = rememberPoseCamera(engine::submit)
    val snapshot by engine.snapshot.collectAsStateWithLifecycle()
    val feed = rememberEventFeed(engine.events)

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        // Camera is 4:3 landscape sensor → 3:4 upright in portrait.
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black)) {
            CameraPreview(camera, Modifier.fillMaxSize())
            SkeletonOverlay(snapshot, Modifier.fillMaxSize())
            EventFlash(feed, Modifier.align(Alignment.Center))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                OutlinedButton(onClick = onBack) { Text("Back") }
                Button(onClick = engine::recalibrate) { Text("Recalibrate") }
            }
            MotionHud(snapshot, feed.recent, camera.source.accelerator.name)
        }
    }
}
