package com.ninjapanda

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.ninjapanda.motion.MotionEngine
import com.ninjapanda.motion.debug.SkeletonOverlay
import com.ninjapanda.motion.debug.trackingMessage
import com.ninjapanda.motion.link.DiscoveredTv
import com.ninjapanda.motion.link.LinkClient
import com.ninjapanda.motion.link.LowLatencyWifiLock
import com.ninjapanda.motion.link.PairingInfo
import com.ninjapanda.motion.link.TvDiscovery
import kotlin.math.roundToInt

@Composable
fun SendToTvScreen(onBack: () -> Unit) {
    var pairing by remember { mutableStateOf<PairingInfo?>(null) }
    val current = pairing
    if (current == null) {
        PairTvScreen(onPaired = { pairing = it }, onBack = onBack)
    } else {
        StreamToTvScreen(current, onStop = { pairing = null })
    }
}

// ---------------------------------------------------------------- Pairing

@Composable
private fun PairTvScreen(onPaired: (PairingInfo) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val discovery = remember { TvDiscovery(context) }
    DisposableEffect(discovery) {
        discovery.start()
        onDispose { discovery.stop() }
    }
    val tvs by discovery.tvs.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<DiscoveredTv?>(null) }
    var manualHost by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun scanQr() {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { code ->
                val info = code.rawValue?.let(PairingInfo::parse)
                if (info != null) onPaired(info) else error = "That isn't a Ninja Panda TV code."
            }
            .addOnFailureListener { error = "Scanner unavailable: ${it.message}. Use the PIN instead." }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Send to TV", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Open Ninja Panda TV on your TV. Phone and TV must be on the same Wi-Fi.", color = Color.Gray)

        Button(onClick = ::scanQr, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("Scan the code on the TV", fontSize = 18.sp)
        }
        error?.let { Text(it, color = Color(0xFFF87171)) }

        HorizontalDivider()
        Text("Or pick your TV and enter its PIN", fontWeight = FontWeight.SemiBold)
        if (tvs.isEmpty()) {
            Text("Looking for TVs on this Wi-Fi…", color = Color.Gray, fontSize = 14.sp)
        }
        tvs.forEach { tv ->
            val isSelected = tv == selected
            Text(
                "📺  ${tv.name}   (${tv.host})",
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color(0xFF14532D) else Color(0xFF1F2937))
                    .clickable { selected = tv; manualHost = "" }
                    .padding(14.dp),
            )
        }

        Text("Not listed? Type the TV address shown on the TV:", color = Color.Gray, fontSize = 14.sp)
        OutlinedTextField(
            value = manualHost,
            onValueChange = { manualHost = it.trim(); selected = null },
            label = { Text("TV address, e.g. 192.168.1.20") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { v -> pin = v.filter(Char::isDigit).take(6) },
            label = { Text("6-digit PIN shown on the TV") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        val target = selected
        val canConnect = pin.length == 6 && (target != null || manualHost.isNotBlank())
        Button(
            enabled = canConnect,
            onClick = {
                onPaired(
                    if (target != null) PairingInfo.fromPin(target.host, pin, target.udpPort, target.tcpPort, target.name)
                    else PairingInfo.fromPin(manualHost, pin),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect with PIN") }

        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
}

// ---------------------------------------------------------------- Streaming

@Composable
private fun StreamToTvScreen(pairing: PairingInfo, onStop: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { MotionEngine() } // local copy, only for the preview overlay
    val client = remember(pairing) {
        // Same clock as MediaPipe's frame timestamps, so the TV can measure true lag.
        LinkClient(pairing, deviceName = Build.MODEL, clock = SystemClock::uptimeMillis)
    }
    val wifiLock = remember { LowLatencyWifiLock(context) }
    val camera = rememberPoseCamera { frame ->
        client.send(frame)
        engine.submit(frame)
    }
    DisposableEffect(client) {
        client.start(scope)
        wifiLock.acquire()
        onDispose {
            client.stop()
            wifiLock.release()
        }
    }

    val state by client.state.collectAsStateWithLifecycle()
    val sent by client.framesSent.collectAsStateWithLifecycle()
    val snapshot by engine.snapshot.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (val s = state) {
                is LinkClient.State.Connected -> {
                    Text("Sending to ${s.tvName}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4ADE80))
                    Text("${snapshot.poseRateHz.roundToInt()} frames/s · $sent sent · ${camera.source.accelerator}", color = Color.Gray, fontSize = 14.sp)
                }
                is LinkClient.State.Connecting -> {
                    Text("Connecting to ${pairing.tvName}…", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    s.lastError?.let { Text("Last try: $it", color = Color.Gray, fontSize = 13.sp) }
                }
                is LinkClient.State.Failed -> {
                    Text(s.reason, fontSize = 18.sp, color = Color(0xFFF87171))
                    Button(onClick = onStop) { Text("Pair again") }
                }
                LinkClient.State.Idle -> Text("Starting…")
            }
        }

        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black)) {
            CameraPreview(camera, Modifier.fillMaxSize())
            SkeletonOverlay(snapshot, Modifier.fillMaxSize())
            Text(
                trackingMessage(snapshot),
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
            )
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Prop the phone up 2–3 m away with your whole body in view.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
    }
}
