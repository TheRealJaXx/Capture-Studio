package com.example.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

interface ControlServerListener {
    fun onRemoteConnected(remoteIp: String)
    fun onRemoteDisconnected()
    fun onSetGain(source: String, value: Float)
    fun onSetMute(source: String, muted: Boolean)
    fun onButtonClicked(buttonId: Int)
    fun onSetLocalMonitor(enabled: Boolean)
    fun onAudioStartRequested(remoteIp: String)
    fun onAudioStopRequested()
}

/**
 * Feed-side control server handling TCP control link, mDNS advertisement, and pairing PIN verification.
 */
class ControlServer(
    private val context: Context,
    val pairingPin: String,
    private val listener: ControlServerListener
) {
    companion object {
        private const val TAG = "ControlServer"
        private const val PING_INTERVAL_MS = 2000L
        private const val TIMEOUT_MS = 6000L
        private const val METER_INTERVAL_MS = 50L // 20 Hz
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val pairingToken = UUID.randomUUID().toString().take(8)
    private var activeClient: ClientSession? = null
    private val isRunning = AtomicBoolean(false)

    // Current state caching
    @Volatile
    private var currentState = Protocol.FeedState()

    // Current meter caching
    @Volatile
    private var currentMicMeter = Protocol.SourceMeter.SILENCE
    @Volatile
    private var currentCaptureMeter = Protocol.SourceMeter.SILENCE

    fun getPairingToken(): String = pairingToken

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get local IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        registerNsdService()

        scope.launch {
            try {
                serverSocket = ServerSocket(Protocol.CONTROL_PORT).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "ControlServer started on port ${Protocol.CONTROL_PORT}")

                while (isActive && isRunning.get()) {
                    val socket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: SocketException) {
                        break
                    } catch (e: Throwable) {
                        Log.e(TAG, "Accept error: ${e.message}")
                        break
                    }

                    Log.i(TAG, "Accepted incoming connection from ${socket.inetAddress.hostAddress}")
                    // Replace existing connection if any
                    activeClient?.close()
                    val session = ClientSession(socket)
                    activeClient = session
                    session.start()
                }
            } catch (e: Throwable) {
                if (isRunning.get()) {
                    Log.e(TAG, "Server socket error: ${e.message}", e)
                }
            }
        }

        // Start 20 Hz meter broadcast loop
        scope.launch {
            while (isActive && isRunning.get()) {
                delay(METER_INTERVAL_MS)
                val client = activeClient
                if (client != null && client.isAuthenticated) {
                    val msg = Protocol.createMeterMessage(currentMicMeter, currentCaptureMeter)
                    client.sendMessage(msg)
                }
            }
        }
    }

    private fun registerNsdService() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            val serviceInfo = NsdServiceInfo().apply {
                serviceName = "${Protocol.SERVICE_NAME_PREFIX}-${getLocalIpAddress().replace('.', '-')}"
                serviceType = Protocol.SERVICE_TYPE
                port = Protocol.CONTROL_PORT
            }

            registrationListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "mDNS Service registered: ${serviceInfo?.serviceName}")
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "mDNS Registration failed: $errorCode")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                    Log.i(TAG, "mDNS Service unregistered")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    Log.e(TAG, "mDNS Unregistration failed: $errorCode")
                }
            }

            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register NSD service: ${e.message}")
        }
    }

    fun updateState(state: Protocol.FeedState) {
        currentState = state
        activeClient?.let {
            if (it.isAuthenticated) {
                it.sendMessage(Protocol.createStateMessage(state))
            }
        }
    }

    fun updateMeters(mic: Protocol.SourceMeter, capture: Protocol.SourceMeter) {
        currentMicMeter = mic
        currentCaptureMeter = capture
    }

    private inner class ClientSession(val socket: Socket) {
        @Volatile
        var isAuthenticated = false
        private var job: Job? = null
        private var lastPongReceived = System.currentTimeMillis()
        private var writer: BufferedWriter? = null

        fun start() {
            job = scope.launch {
                handleClient()
            }
        }

        fun sendMessage(line: String) {
            scope.launch {
                try {
                    writer?.write(line)
                    writer?.newLine()
                    writer?.flush()
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to send message: ${e.message}")
                    close()
                }
            }
        }

        private suspend fun handleClient() = withContext(Dispatchers.IO) {
            val remoteIp = socket.inetAddress.hostAddress ?: "Unknown"
            lastPongReceived = System.currentTimeMillis()

            try {
                socket.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                // Launch heartbeat watchdog
                val heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(PING_INTERVAL_MS)
                        if (System.currentTimeMillis() - lastPongReceived > TIMEOUT_MS) {
                            Log.w(TAG, "Remote heartbeat timed out ($TIMEOUT_MS ms)")
                            close()
                            break
                        }
                        sendMessage(Protocol.createPingMessage())
                    }
                }

                var line: String?
                while (isRunning.get() && socket.isConnected && !socket.isClosed) {
                    line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    handleMessage(line, remoteIp)
                }

                heartbeatJob.cancel()
            } catch (e: Throwable) {
                if (isRunning.get()) {
                    Log.w(TAG, "Client session error: ${e.message}")
                }
            } finally {
                close()
                if (isAuthenticated) {
                    listener.onRemoteDisconnected()
                }
            }
        }

        private fun handleMessage(raw: String, remoteIp: String) {
            try {
                val json = JSONObject(raw)
                when (json.optString("t")) {
                    Protocol.TYPE_HELLO -> {
                        val pin = json.optString("pin")
                        val token = json.optString("token")
                        if (token == pairingToken || pin == pairingPin) {
                            isAuthenticated = true
                            lastPongReceived = System.currentTimeMillis()
                            sendMessage(Protocol.createHelloAckMessage(true, pairingToken, null))
                            sendMessage(Protocol.createStateMessage(currentState))
                            listener.onRemoteConnected(remoteIp)
                            Log.i(TAG, "Remote paired and authenticated successfully")
                        } else {
                            sendMessage(Protocol.createHelloAckMessage(false, null, "Invalid PIN"))
                            close()
                        }
                    }

                    Protocol.TYPE_PING -> {
                        sendMessage(Protocol.createPongMessage())
                    }

                    Protocol.TYPE_PONG -> {
                        lastPongReceived = System.currentTimeMillis()
                    }

                    Protocol.TYPE_SET_GAIN -> {
                        if (!isAuthenticated) return
                        val src = json.optString("src")
                        val v = json.optDouble("v", 1.0).toFloat()
                        listener.onSetGain(src, v)
                    }

                    Protocol.TYPE_SET_MUTE -> {
                        if (!isAuthenticated) return
                        val src = json.optString("src")
                        val v = json.optBoolean("v", false)
                        listener.onSetMute(src, v)
                    }

                    Protocol.TYPE_BUTTON -> {
                        if (!isAuthenticated) return
                        val id = json.optInt("id", 1)
                        listener.onButtonClicked(id)
                    }

                    Protocol.TYPE_SET_LOCAL_MONITOR -> {
                        if (!isAuthenticated) return
                        val v = json.optBoolean("v", true)
                        listener.onSetLocalMonitor(v)
                    }

                    Protocol.TYPE_AUDIO_START -> {
                        if (!isAuthenticated) return
                        listener.onAudioStartRequested(remoteIp)
                    }

                    Protocol.TYPE_AUDIO_STOP -> {
                        if (!isAuthenticated) return
                        listener.onAudioStopRequested()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to parse message: $raw: ${e.message}")
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Throwable) {
            }
            job?.cancel()
            job = null
            if (activeClient === this) {
                activeClient = null
            }
        }
    }

    fun stop() {
        isRunning.set(false)
        activeClient?.close()
        activeClient = null

        try {
            serverSocket?.close()
        } catch (_: Throwable) {
        }
        serverSocket = null

        try {
            if (registrationListener != null && nsdManager != null) {
                nsdManager?.unregisterService(registrationListener)
            }
        } catch (_: Throwable) {
        }
        registrationListener = null
        nsdManager = null

        scope.cancel()
        Log.i(TAG, "ControlServer stopped")
    }
}
