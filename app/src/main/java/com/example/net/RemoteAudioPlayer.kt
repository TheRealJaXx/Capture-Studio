package com.example.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

data class RemoteAudioStats(
    val latencyMs: Long = 0,
    val packetLossPercent: Float = 0f,
    val bufferDepthMs: Int = 40,
    val isPlaying: Boolean = false
)

/**
 * Remote UDP Audio Player featuring low-latency AudioTrack, sequence-ordered jitter buffer,
 * drift compensation, and packet loss telemetry.
 */
class RemoteAudioPlayer(
    private val context: Context,
    private val onStatsUpdated: (RemoteAudioStats) -> Unit
) {
    companion object {
        private const val TAG = "RemoteAudioPlayer"
        private const val SAMPLE_RATE = 48000
        private const val PACKET_DURATION_MS = 10
        private const val SAMPLES_PER_PACKET = 960 // 480 frames * 2 channels = 960 shorts
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var udpSocket: DatagramSocket? = null
    private var playbackThread: Thread? = null
    private var receiveThread: Thread? = null

    private val isRunning = AtomicBoolean(false)
    private var monitorVolume = 1.0f
    private var targetJitterDepthMs = 40 // default 40ms

    // Jitter buffer queue sorted by sequence number
    private val jitterBuffer = TreeMap<Long, ShortArray>()
    private val bufferLock = Object()

    // Telemetry tracking
    private var expectedSeq = -1L
    private var totalPacketsExpected = 0L
    private var totalPacketsLost = 0L
    private var lastMeasuredLatency = 0L
    private var lastStatsPush = 0L

    private var wifiLock: WifiManager.WifiLock? = null

    fun setTargetJitterDepth(ms: Int) {
        targetJitterDepthMs = ms.coerceIn(20, 120)
    }

    fun setMonitorVolume(volume: Float) {
        monitorVolume = volume.coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(monitorVolume)
        } catch (_: Throwable) {
        }
    }

    fun getMonitorVolume(): Float = monitorVolume

    fun start(feedHost: String, pairingToken: String) {
        if (isRunning.getAndSet(true)) return

        acquireWifiLock()
        requestAudioFocus()

        try {
            val sock = DatagramSocket().apply {
                soTimeout = 2000
                try {
                    trafficClass = 0xB8
                } catch (_: Throwable) {
                }
            }
            udpSocket = sock

            initAudioTrack()

            // Receiver thread
            receiveThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                receiveLoop(sock, feedHost, pairingToken)
            }, "RemoteAudio-Receiver").apply { start() }

            // Playback thread
            playbackThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                playbackLoop()
            }, "RemoteAudio-Playback").apply { start() }

            Log.i(TAG, "RemoteAudioPlayer started for Feed at $feedHost")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start RemoteAudioPlayer: ${e.message}", e)
            stop()
        }
    }

    private fun initAudioTrack() {
        val channelOut = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelOut, encoding)
        val trackBuf = (minBuf * 2).coerceAtLeast(1920)

        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(encoding)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(channelOut)
            .build()

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attr)
            .setAudioFormat(format)
            .setBufferSizeInBytes(trackBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        audioTrack = builder.build().apply {
            setVolume(monitorVolume)
            play()
        }
    }

    private fun receiveLoop(sock: DatagramSocket, feedHost: String, pairingToken: String) {
        val feedAddr = try {
            InetAddress.getByName(feedHost)
        } catch (e: Throwable) {
            Log.e(TAG, "Invalid feed host: $feedHost: ${e.message}")
            return
        }

        // Send registration UDP packet to Feed
        val regMsg = "REGISTER:$pairingToken".toByteArray()
        val regPacket = DatagramPacket(regMsg, regMsg.size, feedAddr, Protocol.AUDIO_UDP_PORT)
        try {
            sock.send(regPacket)
            Log.i(TAG, "Sent UDP register packet to $feedHost:${Protocol.AUDIO_UDP_PORT}")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to send UDP register: ${e.message}")
        }

        val rawBuf = ByteArray(AudioStreamer.PACKET_SIZE)
        val packet = DatagramPacket(rawBuf, rawBuf.size)

        while (isRunning.get() && !sock.isClosed) {
            try {
                sock.receive(packet)
                val len = packet.length
                if (len < AudioStreamer.HEADER_SIZE) continue

                val data = packet.data

                // Parse 12-byte header
                val seq = (((data[0].toLong() and 0xFF) shl 24) or
                        ((data[1].toLong() and 0xFF) shl 16) or
                        ((data[2].toLong() and 0xFF) shl 8) or
                        (data[3].toLong() and 0xFF))

                val timestamp = (((data[4].toLong() and 0xFF) shl 24) or
                        ((data[5].toLong() and 0xFF) shl 16) or
                        ((data[6].toLong() and 0xFF) shl 8) or
                        (data[7].toLong() and 0xFF))

                val payloadBytes = len - AudioStreamer.HEADER_SIZE
                val samplesCount = payloadBytes / 2
                val samples = ShortArray(samplesCount)

                var byteIdx = AudioStreamer.HEADER_SIZE
                for (i in 0 until samplesCount) {
                    val low = data[byteIdx++].toInt() and 0xFF
                    val high = data[byteIdx++].toInt() shl 8
                    samples[i] = (high or low).toShort()
                }

                val now = System.currentTimeMillis()
                lastMeasuredLatency = (now - timestamp).coerceAtLeast(0)

                // Track packet loss
                if (expectedSeq != -1L && seq > expectedSeq) {
                    val lost = (seq - expectedSeq)
                    totalPacketsLost += lost
                    totalPacketsExpected += lost + 1
                } else {
                    totalPacketsExpected++
                }
                expectedSeq = seq + 1

                // Insert into jitter buffer
                val maxPackets = (targetJitterDepthMs / PACKET_DURATION_MS) + 4
                synchronized(bufferLock) {
                    jitterBuffer[seq] = samples
                    // Clock drift compensation: drop oldest frames if queue exceeds limit
                    while (jitterBuffer.size > maxPackets) {
                        jitterBuffer.pollFirstEntry()
                    }
                    bufferLock.notify()
                }
            } catch (_: SocketTimeoutException) {
                // Send keepalive re-registration on receive timeout
                try {
                    sock.send(regPacket)
                } catch (_: Throwable) {
                }
            } catch (e: Throwable) {
                if (isRunning.get()) {
                    Log.w(TAG, "Receive error: ${e.message}")
                }
            }
        }
    }

    private fun playbackLoop() {
        var playSeq = -1L
        val silence = ShortArray(SAMPLES_PER_PACKET)
        val targetPackets = (targetJitterDepthMs / PACKET_DURATION_MS).coerceAtLeast(2)

        while (isRunning.get()) {
            var chunkToPlay: ShortArray? = null

            synchronized(bufferLock) {
                // Initial buffer pre-fill
                while (isRunning.get() && playSeq == -1L && jitterBuffer.size < targetPackets) {
                    try {
                        bufferLock.wait(100)
                    } catch (_: InterruptedException) {
                        return
                    }
                }

                if (!isRunning.get()) return

                if (playSeq == -1L) {
                    playSeq = jitterBuffer.firstKey()
                }

                if (jitterBuffer.containsKey(playSeq)) {
                    chunkToPlay = jitterBuffer.remove(playSeq)
                    playSeq++
                } else {
                    // Check if there is a newer packet ahead
                    val nextKey = jitterBuffer.higherKey(playSeq)
                    if (nextKey != null && nextKey - playSeq <= 3) {
                        // Small gap: conceal with silence/gap filling
                        chunkToPlay = silence
                        playSeq++
                    } else if (nextKey != null) {
                        // Large gap: resync to nextKey
                        playSeq = nextKey
                        chunkToPlay = jitterBuffer.remove(playSeq)
                        playSeq++
                    } else {
                        // Buffer underrun, wait briefly
                        try {
                            bufferLock.wait(10)
                        } catch (_: InterruptedException) {
                            return
                        }
                    }
                }
            }

            if (chunkToPlay != null && isRunning.get()) {
                audioTrack?.write(chunkToPlay, 0, chunkToPlay.size)
            }

            val now = System.currentTimeMillis()
            if (now - lastStatsPush > 500) {
                lastStatsPush = now
                val lossRate = if (totalPacketsExpected > 0) {
                    (totalPacketsLost.toFloat() / totalPacketsExpected.toFloat()) * 100f
                } else 0f

                val depthMs = synchronized(bufferLock) { jitterBuffer.size * PACKET_DURATION_MS }
                onStatsUpdated(
                    RemoteAudioStats(
                        latencyMs = lastMeasuredLatency,
                        packetLossPercent = lossRate,
                        bufferDepthMs = depthMs,
                        isPlaying = isRunning.get()
                    )
                )
            }
        }
    }

    private fun requestAudioFocus() {
        try {
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        } catch (_: Throwable) {
        }
    }

    private fun abandonAudioFocus() {
        try {
            audioManager.abandonAudioFocus(null)
        } catch (_: Throwable) {
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
                wifiLock = wm.createWifiLock(mode, "CaptureStudio:RemoteAudioPlayer").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    private fun releaseWifiLock() {
        try {
            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        synchronized(bufferLock) {
            bufferLock.notifyAll()
        }

        try {
            udpSocket?.close()
        } catch (_: Throwable) {
        }
        udpSocket = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        audioTrack = null

        abandonAudioFocus()
        releaseWifiLock()

        receiveThread?.interrupt()
        playbackThread?.interrupt()
        receiveThread = null
        playbackThread = null

        synchronized(bufferLock) {
            jitterBuffer.clear()
        }
        expectedSeq = -1L
        Log.i(TAG, "RemoteAudioPlayer stopped")
    }
}
