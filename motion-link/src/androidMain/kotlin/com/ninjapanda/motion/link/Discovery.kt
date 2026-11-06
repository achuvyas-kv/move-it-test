package com.ninjapanda.motion.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

/** TV side: announces "a Move It TV is here" on the local network via mDNS/NSD. */
class TvAdvertiser(context: Context, private val tvName: String, private val tcpPort: Int, private val udpPort: Int) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    fun start() {
        if (listener != null) return
        val info = NsdServiceInfo().apply {
            serviceName = tvName
            serviceType = LinkDefaults.SERVICE_TYPE
            port = tcpPort
            setAttribute("udp", udpPort.toString())
            setAttribute("v", LinkDefaults.PROTOCOL_VERSION.toString())
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Advertising as ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Advertise failed: $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
        }
        listener = l
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { Log.w(TAG, "registerService", it) }
    }

    fun stop() {
        listener?.let { runCatching { nsd.unregisterService(it) } }
        listener = null
    }
}

data class DiscoveredTv(val name: String, val host: String, val tcpPort: Int, val udpPort: Int)

/**
 * Phone side: finds Move It TVs on the local network. The user still enters the TV's PIN
 * (or scans its QR code), since discovery alone proves nothing about who is on the network.
 */
class TvDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()

    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (discovery != null) return
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("moveit:discovery").apply { setReferenceCounted(false); acquire() }
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Discovery failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { enqueue(info) }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { _tvs.value = _tvs.value.filterNot { it.name == info.serviceName } }
            }
        }
        discovery = l
        runCatching { nsd.discoverServices(LinkDefaults.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { Log.w(TAG, "discoverServices", it) }
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        multicastLock?.release()
        multicastLock = null
        resolveQueue.clear()
        _tvs.value = emptyList()
    }

    // The classic resolve API handles one request at a time, so queue them.
    private fun enqueue(info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                main.post { resolving = false; resolveNext() }
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = (info.host as? Inet4Address)?.hostAddress
                val udp = info.attributes["udp"]?.decodeToString()?.toIntOrNull() ?: LinkDefaults.UDP_PORT
                main.post {
                    if (host != null) {
                        val tv = DiscoveredTv(info.serviceName, host, info.port, udp)
                        _tvs.value = _tvs.value.filterNot { it.name == tv.name } + tv
                    }
                    resolving = false
                    resolveNext()
                }
            }
        })
    }
}

private const val TAG = "MoveItLink"
