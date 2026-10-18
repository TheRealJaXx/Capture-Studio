package com.example.net

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Feed-side UDP audio streaming engine.
 * Streams 48kHz 16-bit stereo PCM chunks (10ms = 1920 bytes) with a 12-byte header
 * to the connected Remote with low latency and DSCP 0xB8.
 */
class AudioStreamer(private val context: Context) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val HEADER_SIZE = 12
        const val PAYLOAD_SIZE = 1920 // 480 frames * 2 channels * 2 bytes = 1920 bytes (10ms at 48kHz)
        const val PACKET_SIZE = HEADER_SIZE + PAYLOAD_SIZE
        const val QUEUE_CAPACITY = 16
        const val FLAG_48K_STEREO = 0x0001
    }

    private var socket: DatagramSocket? = null
    private var isStreaming = AtomicBoolean(false)
    private var isRunning = AtomicBoolean(false)

    private var targetAddress: InetAddress? = null
    private var targetPort: Int = Protocol.AUDIO_UDP_PORT
    private var expectedToken: String? = null

    private var wifiLock: WifiManager.WifiLock? = null
    private val packetQueue = ArrayBlockingQueue<ByteArray>(QUEUE_CAPACITY)

    // Pre-allocated packet pool to avoid heap allocations in the audio thread
    private val bufferPool = Array(QUEUE_CAPACITY + 2) { ByteArray(PACKET_SIZE) }
    private var poolIndex = 0

    private var sequenceNumber = 0L
    private var sendThread: Thread? = null
    private var receiveThread: Thread? = null

    fun start(pairingToken: String) {
        if (isRunning.getAndSet(true)) return
        expectedToken = pairingToken

        try {
            val sock = DatagramSocket(Protocol.AUDIO_UDP_PORT).apply {
                reuseAddress = true
                try {
                    trafficClass = 0xB8 // Expedited Forwarding / Voice low-delay
                } catch (e: Throwable) {
                    Log.w(TAG, "Failed to set trafficClass: ${e.message}")
                }
            }
            socket = sock

            // Acquire low-latency Wi-Fi lock
            acquireWifiLock()

            // Start UDP registration listener thread
            receiveThread = Thread({
                listenForRegistration(sock)
            }, "AudioStreamer-RegListener").apply { start() }

            // Start UDP sender thread
            sendThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                sendLoop(sock)
            }, "AudioStreamer-Sender").apply { start() }

            Log.d(TAG, "AudioStreamer started on port ${Protocol.AUDIO_UDP_PORT}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start AudioStreamer: ${e.message}", e)
        }
    }

    fun setStreamingEnabled(enabled: Boolean) {
        isStreaming.set(enabled)
        Log.i(TAG, "Audio streaming enabled: $enabled, target: $targetAddress:$targetPort")
    }

    fun isStreaming(): Boolean = isStreaming.get()

    fun setTargetAddress(address: InetAddress, port: Int = Protocol.AUDIO_UDP_PORT) {
        targetAddress = address
        targetPort = port
        Log.i(TAG, "Target audio destination set to $address:$port")
    }

    /**
     * Tapped directly from the audio loop.
     * ZERO allocations in the loop; uses preallocated packet pool and non-blocking queue offer.
     */
    fun sendPcmChunk(pcmData: ShortArray, shortsLength: Int, timestampMs: Long) {
        if (!isStreaming.get() || targetAddress == null) return
        val bytesNeeded = shortsLength * 2
        if (bytesNeeded != PAYLOAD_SIZE) return

        // Pick next pre-allocated buffer from pool
        val buffer = bufferPool[poolIndex]
        poolIndex = (poolIndex + 1) % bufferPool.size

        // Write 12-byte header:
        // [0..3]: uint32 sequence number (big-endian)
        // [4..7]: uint32 timestamp ms
        // [8..9]: uint16 flags
        // [10..11]: uint16 reserved
        val seq = sequenceNumber++
        buffer[0] = ((seq shr 24) and 0xFF).toByte()
        buffer[1] = ((seq shr 16) and 0xFF).toByte()
        buffer[2] = ((seq shr 8) and 0xFF).toByte()
        buffer[3] = (seq and 0xFF).toByte()

        buffer[4] = ((timestampMs shr 24) and 0xFF).toByte()
        buffer[5] = ((timestampMs shr 16) and 0xFF).toByte()
        buffer[6] = ((timestampMs shr 8) and 0xFF).toByte()
        buffer[7] = (timestampMs and 0xFF).toByte()

        buffer[8] = ((FLAG_48K_STEREO shr 8) and 0xFF).toByte()
        buffer[9] = (FLAG_48K_STEREO and 0xFF).toByte()
        buffer[10] = 0
        buffer[11] = 0

        // Copy PCM short data into bytes (little-endian PCM16)
        var byteOffset = HEADER_SIZE
        for (i in 0 until shortsLength) {
            val sample = pcmData[i].toInt()
            buffer[byteOffset++] = (sample and 0xFF).toByte()
            buffer[byteOffset++] = ((sample shr 8) and 0xFF).toByte()
        }

        // Offer to queue (non-blocking; drop on overflow)
        if (!packetQueue.offer(buffer)) {
            // Queue full, drop packet to preserve low latency
            packetQueue.poll()
            packetQueue.offer(buffer)
        }
    }

    private fun sendLoop(sock: DatagramSocket) {
        val datagramPacket = DatagramPacket(ByteArray(0), 0)

        while (isRunning.get()) {
            val packetData = try {
                packetQueue.take()
            } catch (_: InterruptedException) {
                break
            }

            val target = targetAddress
            if (isStreaming.get() && target != null && !sock.isClosed) {
                try {
                    datagramPacket.data = packetData
                    datagramPacket.length = packetData.size
                    datagramPacket.address = target
                    datagramPacket.port = targetPort
                    sock.send(datagramPacket)
                } catch (e: Throwable) {
                    if (isRunning.get()) {
                        Log.w(TAG, "Error sending UDP audio packet: ${e.message}")
                    }
                }
            }
        }
    }

    private fun listenForRegistration(sock: DatagramSocket) {
        val recvBuf = ByteArray(256)
        val packet = DatagramPacket(recvBuf, recvBuf.size)

        while (isRunning.get() && !sock.isClosed) {
            try {
                sock.receive(packet)
                val str = String(packet.data, 0, packet.length).trim()
                Log.d(TAG, "Received UDP register message: $str from ${packet.address}:${packet.port}")

                if (str.startsWith("REGISTER:")) {
                    val token = str.removePrefix("REGISTER:")
                    if (expectedToken == null || token == expectedToken) {
                        targetAddress = packet.address
                        targetPort = packet.port
                        Log.i(TAG, "Registered Remote audio streaming target: $targetAddress:$targetPort")
                        // Send confirmation ACK back to Remote
                        val ack = "ACK".toByteArray()
                        sock.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
                    } else {
                        Log.w(TAG, "Rejecting UDP register token mismatch: $token vs $expectedToken")
                    }
                }
            } catch (e: SocketException) {
                break
            } catch (e: Throwable) {
                if (isRunning.get()) {
                    Log.w(TAG, "UDP receive error: ${e.message}")
                }
            }
        }
    }

    private fun acquireWifiLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "CaptureStudio:AudioStreamer").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "WifiLock acquired for low-latency audio streaming")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to release WifiLock: ${e.message}")
        }
    }

    fun stop() {
        isRunning.set(false)
        isStreaming.set(false)
        try {
            socket?.close()
        } catch (_: Throwable) {
        }
        socket = null
        releaseWifiLock()

        sendThread?.interrupt()
        receiveThread?.interrupt()
        sendThread = null
        receiveThread = null
        packetQueue.clear()
        Log.d(TAG, "AudioStreamer stopped")
    }
}
