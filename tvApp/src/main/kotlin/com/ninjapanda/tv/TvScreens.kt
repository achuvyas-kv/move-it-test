package com.ninjapanda.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ninjapanda.motion.MotionEngine
import com.ninjapanda.motion.debug.EventFlash
import com.ninjapanda.motion.debug.MotionHud
import com.ninjapanda.motion.debug.SkeletonOverlay
import com.ninjapanda.motion.debug.rememberEventFeed
import com.ninjapanda.motion.link.LinkServer
import com.ninjapanda.motion.link.LinkStats
import com.ninjapanda.motion.link.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun TvApp(server: LinkServer, engine: MotionEngine, tvName: String) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0F17)) {
            val state by server.state.collectAsStateWithLifecycle()
            when (val s = state) {
                is LinkServer.State.Connected -> ConnectedScreen(server, engine, s.phoneName)
                else -> PairingScreen(server, tvName, s)
            }
        }
    }
}

@Composable
private fun PairingScreen(server: LinkServer, tvName: String, state: LinkServer.State) {
    // The TV's address can change (Wi-Fi reconnects), so keep it fresh.
    var ip by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            ip = withContext(Dispatchers.IO) { NetworkUtils.localIpv4() }
            delay(5_000)
        }
    }

    Row(
        Modifier.fillMaxSize().padding(48.dp),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val host = ip
        if (host != null) {
            QrCode(server.pairingInfo(host).toUri(), Modifier.size(340.dp).clip(RoundedCornerShape(16.dp)))
        } else {
            Box(Modifier.size(340.dp).background(Color.DarkGray, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Text("No Wi-Fi", fontSize = 28.sp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Play with your phone", fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text("1.  Open Ninja Panda on your phone", fontSize = 22.sp)
            Text("2.  Tap  Send to TV", fontSize = 22.sp)
            Text("3.  Scan this code", fontSize = 22.sp)
            Spacer(Modifier.height(16.dp))
            Text("Can't scan? Pick \"$tvName\" on your phone and enter:", fontSize = 18.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Labeled("PIN", server.pin.chunked(3).joinToString(" "))
                Labeled("TV address", host ?: "—")
            }
            Spacer(Modifier.height(8.dp))
            val status = when (state) {
                is LinkServer.State.Failed -> "Network error: ${state.reason}"
                LinkServer.State.Listening -> "Waiting for a phone…"
                else -> "Starting…"
            }
            Text(status, fontSize = 18.sp, color = if (state is LinkServer.State.Failed) Color(0xFFF87171) else Color(0xFF4ADE80))
            Text("Phone and TV must be on the same Wi-Fi.", fontSize = 16.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Column {
        Text(label, fontSize = 14.sp, color = Color.Gray)
        Text(value, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ConnectedScreen(server: LinkServer, engine: MotionEngine, phoneName: String) {
    val snapshot by engine.snapshot.collectAsStateWithLifecycle()
    val stats by server.stats.collectAsStateWithLifecycle()
    val feed = rememberEventFeed(engine.events)

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        // Skeleton in the phone camera's proportions (portrait 3:4 when the phone stands upright).
        Box(
            Modifier.fillMaxHeight().aspectRatio(snapshot.aspectRatio.takeIf { it > 0.2f } ?: 0.75f)
                .clip(RoundedCornerShape(12.dp)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            SkeletonOverlay(snapshot, Modifier.fillMaxSize())
            EventFlash(feed, fontSize = 56.sp)
        }
        Column(Modifier.width(520.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connected: $phoneName", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
            LinkStatsPanel(stats)
            Button(onClick = engine::recalibrate) { Text("Recalibrate") }
            MotionHud(snapshot, feed.recent, accelerator = "Phone")
        }
    }
}

@Composable
private fun LinkStatsPanel(stats: LinkStats) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1F2937)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Phone → TV lag", fontSize = 14.sp, color = Color.Gray)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Bottom) {
            Big("avg", stats.lagAvgMs)
            Big("p95", stats.lagP95Ms)
            Big("max", stats.lagMaxMs)
        }
        Mono("${stats.packetsPerSecond.roundToInt()} frames/s · loss ${stats.lossPercent.roundToInt()}% · jitter ${ms(stats.jitterMs)}")
        Mono("Wi-Fi round trip ${stats.rttMs?.let { "$it ms" } ?: "…"} · phone tracking ${stats.phoneLatencyMs} ms")
        Mono("received ${stats.totalReceived} · late ${stats.lateDropped} · rejected ${stats.rejected}")
        Text(
            "Lag = phone camera frame → arrival on TV (includes tracking on the phone). Excludes the TV's own display delay.",
            fontSize = 12.sp, color = Color.Gray,
        )
    }
}

@Composable
private fun Big(label: String, value: Float?) {
    val color = when {
        value == null -> Color.Gray
        value < 50f -> Color(0xFF4ADE80)
        value < 100f -> Color(0xFFFACC15)
        else -> Color(0xFFF87171)
    }
    Column {
        Text(value?.let { "${it.roundToInt()}" } ?: "…", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = color)
        Text("$label ms", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun Mono(text: String) = Text(text, fontFamily = FontFamily.Monospace, fontSize = 14.sp)

private fun ms(v: Float?) = v?.let { "${it.roundToInt()} ms" } ?: "…"
