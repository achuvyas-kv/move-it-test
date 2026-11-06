package com.ninjapanda.motion.debug

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ninjapanda.motion.HandState
import com.ninjapanda.motion.Joint
import com.ninjapanda.motion.MotionSnapshot
import com.ninjapanda.motion.Vec2

/**
 * Draws the smoothed skeleton, the reach box and both hand cursors/blade trails on top of
 * a camera preview. The overlay must have the same aspect ratio as the camera frame.
 */
@Composable
fun SkeletonOverlay(snapshot: MotionSnapshot, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val pose = snapshot.pose ?: return@Canvas
        fun pt(v: Vec2) = Offset(v.x * size.width, v.y * size.height)

        val boneColor = if (snapshot.tracking == com.ninjapanda.motion.TrackingState.Ready) Color(0xFF4ADE80) else Color(0xFFFACC15)
        for ((a, b) in Joint.BONES) {
            if (pose.isVisible(a) && pose.isVisible(b)) {
                drawLine(boneColor, pt(pose[a]), pt(pose[b]), strokeWidth = 6f, cap = StrokeCap.Round)
            }
        }
        for (j in Joint.entries) {
            if (pose.isVisible(j)) drawCircle(Color.White, radius = 6f, center = pt(pose[j]))
        }

        snapshot.reachBox?.let { box ->
            drawRect(
                color = Color.White.copy(alpha = 0.35f),
                topLeft = Offset(box.left * size.width, box.top * size.height),
                size = Size(box.width * size.width, box.height * size.height),
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
            )
        }
        drawHand(snapshot, snapshot.leftHand, Color(0xFF38BDF8), ::pt)
        drawHand(snapshot, snapshot.rightHand, Color(0xFFF472B6), ::pt)
    }
}

private fun DrawScope.drawHand(snapshot: MotionSnapshot, hand: HandState, color: Color, pt: (Vec2) -> Offset) {
    if (!hand.visible) return
    val trail = hand.trail.mapNotNull { snapshot.cursorToFrame(it.position)?.let(pt) }
    val trailColor = if (hand.bladeActive) Color(0xFFFF5252) else color.copy(alpha = 0.6f)
    for (i in 1 until trail.size) {
        val w = if (hand.bladeActive) 18f * i / trail.size else 6f
        drawLine(trailColor, trail[i - 1], trail[i], strokeWidth = w, cap = StrokeCap.Round)
    }
    snapshot.cursorToFrame(hand.cursor)?.let { drawCircle(color, radius = if (hand.bladeActive) 22f else 14f, center = pt(it)) }
}
