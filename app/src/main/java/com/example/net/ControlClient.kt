package com.example.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

enum class ConnectionStatus {
    SEARCHING,
    CONNECTING,
    CONNECTED,
    LOST
}

data class DiscoveredFeed(
    val name: String,
    val host: String,
    val port: Int
)

interface ControlClientListener {
    fun onStateReceived(state: Protocol.FeedState)
    fun onMetersReceived(mic: Protocol.SourceMeter, capture: Protocol.SourceMeter)
    fun onAuthFailed(reason: String)
    fun onTokenAcquired(token: String)
}

/**
 * Remote-side control client managing mDNS service discovery, auto-reconnect,
 * heartbeat ping/pong latency measurement, and control protocol messaging.
 */
class ControlClient(
    private val context: Context,
    private val listener: ControlClientListener
) {
    companion object {
        private const val TAG = "ControlClient"
        private const val PING_INTERVAL_MS = 2000L
        private const val TIMEOUT_MS = 6000L
        private const val GAIN_THROTTLE_MS = 33L // ~30 ms = ~30 Hz
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.SEARCHING)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _discoveredFeeds = MutableStateFlow<List<DiscoveredFeed>>(emptyList())
    val discoveredFeeds: StateFlow<List<DiscoveredFeed>> = _discoveredFeeds.asStateFlow()

    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    private var targetHost: String? = null
    private var targetPort: Int = Protocol.CONTROL_PORT
    private var enteredPin: String = "1234"
    private var pairingToken: String? = null

    private var activeSocket: Socket? = null
    private var writer: BufferedWriter? = null
    private var clientJob: Job? = null
    private var isRunning = AtomicBoolean(false)

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private var lastPingSendTime = 0L
    private var lastPongReceivedTime = 0L

    // Throttle gain updates
    private var lastCaptureGainTime = 0L
    private var lastMicGainTime = 0L

    fun startDiscovery() {
        if (isRunning.getAndSet(true)) return

        acquireMulticastLock()
        initNsdDiscovery()
        _connectionStatus.value = ConnectionStatus.SEARCHING
    }

    private fun acquireMulticastLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("CaptureStudioNsdLock")?.apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }
    }

    private fun initNsdDiscovery() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS Discovery started")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service found: ${service.serviceName}")
                    if (service.serviceType.contains("capturestudio", ignoreCase = true)) {
                        resolveService(service)
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "mDNS Service lost: ${service.serviceName}")
                    val current = _discoveredFeeds.value.toMutableList()
                    current.removeAll { it.name == service.serviceName }
                    _discoveredFeeds.value = current
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS Discovery stopped")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS Discovery start failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "mDNS Discovery stop failed: $errorCode")
                }
            }

            nsdManager?.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to start NSD discovery: ${e.message}")
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.w(TAG, "mDNS Resolve failed: $errorCode")
                }

                override fun onServiceResolved(info: NsdServiceInfo?) {
                    info?.let {
                        val host = it.host?.hostAddress ?: return
                        val feed = DiscoveredFeed(
                            name = it.serviceName,
                            host = host,
                            port = it.port
                        )
                        Log.i(TAG, "Resolved Feed: ${feed.name} at ${feed.host}:${feed.port}")
                        val current = _discoveredFeeds.value.toMutableList()
                        if (current.none { f -> f.host == feed.host }) {
                            current.add(feed)
                            _discoveredFeeds.value = current
                        }
                    }
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "Error resolving service: ${e.message}")
        }
    }

    fun connectTo(host: String, port: Int = Protocol.CONTROL_PORT, pin: String, token: String? = null) {
        targetHost = host
        targetPort = port
        enteredPin = pin
        pairingToken = token

        clientJob?.cancel()
        clientJob = scope.launch {
            connectionLoop(host, port)
        }
    }

    fun reconnect() {
        val host = targetHost ?: return
        clientJob?.cancel()
        clientJob = scope.launch {
            connectionLoop(host, targetPort)
        }
    }

    private suspend fun connectionLoop(host: String, port: Int) = withContext(Dispatchers.IO) {
        var backoffMs = 1000L

        while (isActive && isRunning.get()) {
            _connectionStatus.value = ConnectionStatus.CONNECTING
            Log.i(TAG, "Attempting connection to Feed at $host:$port...")

            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 4000)
                activeSocket = socket
                writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // Send HELLO
                val hello = Protocol.createHelloMessage(enteredPin, pairingToken)
                writer?.write(hello)
                writer?.newLine()
                writer?.flush()

                // Wait for HELLO_ACK
                val ackLine = reader.readLine()
                if (ackLine != null) {
                    val ackJson = JSONObject(ackLine)
                    if (ackJson.optString("t") == Protocol.TYPE_HELLO_ACK) {
                        val ok = ackJson.optBoolean("ok", false)
                        if (ok) {
                            val token = ackJson.optString("token")
                            pairingToken = token
                            listener.onTokenAcquired(token)
                            _connectionStatus.value = ConnectionStatus.CONNECTED
                            backoffMs = 1000L // Reset backoff
                            lastPongReceivedTime = System.currentTimeMillis()
                            Log.i(TAG, "Connected and paired with Feed!")

                            // Start heartbeat ping loop
                            val pingJob = scope.launch {
                                while (isActive) {
                                    delay(PING_INTERVAL_MS)
                                    if (System.currentTimeMillis() - lastPongReceivedTime > TIMEOUT_MS) {
                                        Log.w(TAG, "Heartbeat timeout from Feed")
                                        socket.close()
                                        break
                                    }
                                    lastPingSendTime = System.currentTimeMillis()
                                    sendMessage(Protocol.createPingMessage())
                                }
                            }

                            // Read loop
                            var line: String?
                            while (isActive && isRunning.get() && socket.isConnected && !socket.isClosed) {
                                line = reader.readLine() ?: break
                                if (line.isBlank()) continue
                                handleIncomingMessage(line)
                            }

                            pingJob.cancel()
                        } else {
                            val error = ackJson.optString("error", "Invalid PIN")
                            Log.w(TAG, "Authentication failed: $error")
                            _connectionStatus.value = ConnectionStatus.LOST
                            listener.onAuthFailed(error)
                            socket.close()
                            return@withContext
                        }
                    }
                }
            } catch (e: Throwable) {
                if (isRunning.get()) {
                    Log.w(TAG, "Connection failed: ${e.message}")
                }
            } finally {
                try {
                    activeSocket?.close()
                } catch (_: Throwable) {
                }
                activeSocket = null
                writer = null
            }

            if (isActive && isRunning.get()) {
                _connectionStatus.value = ConnectionStatus.LOST
                // Zero meters on disconnect
                listener.onMetersReceived(Protocol.SourceMeter.SILENCE, Protocol.SourceMeter.SILENCE)
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(6000L) // Exponential backoff max 6s
            }
        }
    }

    private fun handleIncomingMessage(line: String) {
        try {
            val json = JSONObject(line)
            when (json.optString("t")) {
                Protocol.TYPE_STATE -> {
                    val state = Protocol.parseStateMessage(json)
                    listener.onStateReceived(state)
                }

                Protocol.TYPE_METER -> {
                    val (mic, cap) = Protocol.parseMeterMessage(json)
                    listener.onMetersReceived(mic, cap)
                }

                Protocol.TYPE_PONG -> {
                    lastPongReceivedTime = System.currentTimeMillis()
                    val rtt = (lastPongReceivedTime - lastPingSendTime).coerceAtLeast(0)
                    _latencyMs.value = rtt
                }

                Protocol.TYPE_PING -> {
                    sendMessage(Protocol.createPongMessage())
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error handling message: $line: ${e.message}")
        }
    }

    private fun sendMessage(msg: String) {
        scope.launch {
            try {
                writer?.write(msg)
                writer?.newLine()
                writer?.flush()
            } catch (e: Throwable) {
                Log.w(TAG, "Error sending message: ${e.message}")
            }
        }
    }

    fun setGain(source: String, value: Float) {
        val now = System.currentTimeMillis()
        if (source == Protocol.SRC_CAPTURE) {
            if (now - lastCaptureGainTime < GAIN_THROTTLE_MS) return
            lastCaptureGainTime = now
        } else if (source == Protocol.SRC_MIC) {
            if (now - lastMicGainTime < GAIN_THROTTLE_MS) return
            lastMicGainTime = now
        }
        sendMessage(Protocol.createSetGainMessage(source, value))
    }

    fun setMute(source: String, muted: Boolean) {
        sendMessage(Protocol.createSetMuteMessage(source, muted))
    }

    fun sendButton(id: String) {
        sendMessage(Protocol.createButtonMessage(id))
    }

    fun sendButton(id: Int) {
        sendButton(id.toString())
    }

    fun startAudioStream() {
        sendMessage(Protocol.createAudioStartMessage())
    }

    fun stopAudioStream() {
        sendMessage(Protocol.createAudioStopMessage())
    }

    fun setLocalMonitor(enabled: Boolean) {
        sendMessage(Protocol.createSetLocalMonitorMessage(enabled))
    }

    fun stop() {
        isRunning.set(false)
        clientJob?.cancel()
        clientJob = null

        try {
            activeSocket?.close()
        } catch (_: Throwable) {
        }
        activeSocket = null
        writer = null

        try {
            if (discoveryListener != null && nsdManager != null) {
                nsdManager?.stopServiceDiscovery(discoveryListener)
            }
        } catch (_: Throwable) {
        }
        discoveryListener = null
        nsdManager = null

        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
        } catch (_: Throwable) {
        }

        scope.cancel()
        _connectionStatus.value = ConnectionStatus.SEARCHING
        Log.d(TAG, "ControlClient stopped")
    }
}
