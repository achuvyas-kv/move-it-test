package com.ninjapanda.motion.link

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    /**
     * This device's IPv4 address on the local network (Wi-Fi or Ethernet), or null when offline.
     * Prefers Ethernet/Wi-Fi interfaces over mobile data and VPNs.
     */
    fun localIpv4(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces()?.toList().orEmpty() }.getOrDefault(emptyList())
            .filter { runCatching { it.isUp && !it.isLoopback && !it.isVirtual }.getOrDefault(false) }
            .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.map { nif.name to it } }
        fun rank(name: String) = when {
            name.startsWith("eth") -> 0
            name.startsWith("wlan") -> 1
            name.startsWith("ap") || name.startsWith("swlan") -> 2 // phone hotspot
            else -> 3
        }
        return candidates.minByOrNull { rank(it.first) }?.second?.hostAddress
    }
}

/**
 * Keeps the Wi-Fi radio out of power-save while the phone and TV are linked.
 * Power-save naps cause 50–200 ms lag spikes on many phones.
 */
class LowLatencyWifiLock(context: Context) {
    private val lock: WifiManager.WifiLock = run {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) WifiManager.WIFI_MODE_FULL_LOW_LATENCY else WifiManager.WIFI_MODE_FULL_HIGH_PERF
        wifi.createWifiLock(mode, "moveit:link").apply { setReferenceCounted(false) }
    }

    fun acquire() { if (!lock.isHeld) lock.acquire() }
    fun release() { if (lock.isHeld) lock.release() }
}
