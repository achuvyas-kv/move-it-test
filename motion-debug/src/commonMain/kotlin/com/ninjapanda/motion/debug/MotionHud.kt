package com.ninjapanda.motion.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ninjapanda.motion.HandState
import com.ninjapanda.motion.Lane
import com.ninjapanda.motion.MotionEvent
import com.ninjapanda.motion.MotionSnapshot
import com.ninjapanda.motion.Posture
import com.ninjapanda.motion.TrackingState
import kotlin.math.roundToInt

/** Live readout of everything the motion pipeline is producing. */
@Composable
fun MotionHud(
    snapshot: MotionSnapshot,
    recentEvents: List<MotionEvent>,
    accelerator: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(trackingMessage(snapshot), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (snapshot.tracking == TrackingState.Calibrating) {
            LinearProgressIndicator(progress = { snapshot.calibrationProgress }, modifier = Modifier.fillMaxWidth())
        }
        Mono("$accelerator · ${snapshot.poseRateHz.roundToInt()} Hz · ${snapshot.latencyMs.roundToInt()} ms")

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Mono("Lean")
            LeanBar(snapshot.lean, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Mono("Lane")
            Lane.entries.forEach { Chip(it.name, active = it == snapshot.lane) }
            Mono("  Pose")
            Chip(snapshot.posture.name, active = snapshot.posture != Posture.Standing)
        }
        HandLine("L hand", snapshot.leftHand)
        HandLine("R hand", snapshot.rightHand)

        Text("Events", style = MaterialTheme.typography.labelLarge)
        recentEvents.takeLast(6).reversed().forEach { Mono(describe(it)) }
    }
}

fun trackingMessage(s: MotionSnapshot): String = when (s.tracking) {
    TrackingState.NoPerson -> "Step in front of the camera"
    TrackingState.NotFullyVisible -> "Show your shoulders and hips"
    TrackingState.TooClose -> "Step back"
    TrackingState.TooFar -> "Come closer"
    TrackingState.Calibrating -> "Stand still… ${(s.calibrationProgress * 100).roundToInt()}%"
    TrackingState.Ready -> "Ready"
}

fun describe(e: MotionEvent): String = when (e) {
    is MotionEvent.Calibrated -> "Calibrated"
    is MotionEvent.PlayerLost -> "Player lost"
    is MotionEvent.Slash -> "SLASH ${e.side} speed ${fmt(e.peakSpeed)}"
    is MotionEvent.Jump -> "JUMP"
    is MotionEvent.DuckStart -> "DUCK"
    is MotionEvent.DuckEnd -> "duck end"
    is MotionEvent.LaneChanged -> "LANE ${e.from} → ${e.to}"
}

@Composable
private fun HandLine(label: String, hand: HandState) {
    val text = if (!hand.visible) "$label  —" else
        "$label  (${fmt(hand.cursor.x)}, ${fmt(hand.cursor.y)})  speed ${fmt(hand.speed)}${if (hand.bladeActive) "  BLADE" else ""}"
    Mono(text, color = if (hand.bladeActive) Color(0xFFFF5252) else Color.Unspecified)
}

@Composable
private fun LeanBar(lean: Float, modifier: Modifier) {
    BoxWithConstraints(modifier.height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.Gray.copy(alpha = 0.3f))) {
        val half = maxWidth / 2
        Box(Modifier.align(Alignment.Center).width(2.dp).height(14.dp).background(Color.Gray))
        Box(
            Modifier.align(Alignment.Center).offset(x = half * lean).width(14.dp).height(14.dp)
                .clip(RoundedCornerShape(7.dp)).background(Color(0xFF4ADE80)),
        )
    }
}

@Composable
private fun Chip(text: String, active: Boolean) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(if (active) Color(0xFF4ADE80) else Color.Gray.copy(alpha = 0.25f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = if (active) Color.Black else Color.Unspecified,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun Mono(text: String, color: Color = Color.Unspecified) {
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = color)
}

private fun fmt(v: Float): String {
    val r = (v * 100).roundToInt()
    val sign = if (r < 0) "-" else ""
    val a = kotlin.math.abs(r)
    return "$sign${a / 100}.${(a % 100).toString().padStart(2, '0')}"
}
