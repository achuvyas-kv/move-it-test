package com.ninjapanda.tv

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.ninjapanda.motion.MotionEngine
import com.ninjapanda.motion.link.LinkServer
import com.ninjapanda.motion.link.LowLatencyWifiLock
import com.ninjapanda.motion.link.PairingInfo
import com.ninjapanda.motion.link.TvAdvertiser
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * TV host: waits for a phone, receives its skeleton over Wi-Fi and runs the shared
 * motion engine on it. Everything runs while the app is in the foreground.
 */
class TvActivity : ComponentActivity() {
    private val engine = MotionEngine()
    private lateinit var server: LinkServer
    private lateinit var advertiser: TvAdvertiser
    private lateinit var wifiLock: LowLatencyWifiLock

    private val requestLocalNetwork = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Ask the TV for its low-latency "Game Mode" (ALLM): saves 30–80 ms on most TVs.
        if (Build.VERSION.SDK_INT >= 30) window.setPreferMinimalPostProcessing(true)
        if (Build.VERSION.SDK_INT >= 37) requestLocalNetwork.launch("android.permission.ACCESS_LOCAL_NETWORK")

        val tvName = Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL
        // New key and PIN every time the app opens: an old QR photo can't be reused later.
        server = LinkServer(tvName = tvName, qrKey = PairingInfo.newKey(), pin = PairingInfo.newPin(), onFrame = engine::submit)
        advertiser = TvAdvertiser(this, tvName, server.tcpPort, server.udpPort)
        wifiLock = LowLatencyWifiLock(this)

        // A new phone (or a reconnect) means a new player: calibrate again.
        lifecycleScope.launch {
            server.state.distinctUntilChangedBy { it is LinkServer.State.Connected }.collect {
                if (it is LinkServer.State.Connected) engine.recalibrate()
            }
        }

        setContent { TvApp(server, engine, tvName) }
    }

    override fun onStart() {
        super.onStart()
        server.start(lifecycleScope)
        advertiser.start()
        wifiLock.acquire()
    }

    override fun onStop() {
        server.stop()
        advertiser.stop()
        wifiLock.release()
        super.onStop()
    }
}
