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
        private const val PACKET_DURATION_MS = 5
        private const val SAMPLES_PER_PACKET = 480 // 5 ms: 240 frames * 2 channels
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var udpSocket: DatagramSocket? = null
    private var playbackThread: Thread? = null
    private var receiveThread: Thread? = null

    private val isRunning = AtomicBoolean(false)
    private var monitorVolume = 1.0f
    private var targetJitterDepthMs = 100 // default 100ms: absorbs Wi-Fi jitter

    // Jitter buffer queue sorted by sequence number
    private val jitterBuffer = TreeMap<Long, ShortArray>()
    private val bufferLock = Object()

    // Telemetry tracking
    private var expectedSeq = -1L
    private var totalPacketsExpected = 0L
    private var totalPacketsLost = 0L
    private var lastMeasuredLatency = 0L
    private var lastStatsPush = 0L

    // Next sequence number the playback thread will play; older (late) packets are discarded on arrival
    @Volatile private var nextPlaySeq = -1L

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
            }
            udpSocket = sock

            initAudioTrack()

            // Receiver thread
            receiveThread = Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    receiveLoop(sock, feedHost, pairingToken)
                } catch (e: Throwable) {
                    Log.e(TAG, "Receive thread crashed: ${e.message}", e)
                }
            }, "RemoteAudio-Receiver").apply { start() }

            // Playback thread
            playbackThread = Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                    playbackLoop()
                } catch (e: Throwable) {
                    Log.e(TAG, "Playback thread crashed: ${e.message}", e)
                }
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
        val trackBuf = (minBuf * 4).coerceAtLeast(3840)

        val attr = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
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
                lastMeasuredLatency = 0L

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
                val maxPackets = (targetJitterDepthMs / PACKET_DURATION_MS) + 8
                synchronized(bufferLock) {
                    // Drop packets that arrive too late to be played
                    if (nextPlaySeq != -1L && seq < nextPlaySeq) {
                        bufferLock.notify()
                        return@synchronized
                    }
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
        var buffering = true // true until the jitter buffer has been (re)filled to the target depth
        val silence = ShortArray(SAMPLES_PER_PACKET)
        val concealBuf = ShortArray(SAMPLES_PER_PACKET)
        var lastChunk: ShortArray? = null
        var concealCount = 0
        // After an underrun only refill a small cushion (20 ms) instead of the full target, so a hiccup costs ~20 ms not ~100 ms
        var refillPackets = targetJitterDepthMs / PACKET_DURATION_MS

        while (isRunning.get()) {
            val targetPackets = (targetJitterDepthMs / PACKET_DURATION_MS).coerceAtLeast(2)
            var chunkToPlay: ShortArray? = null

            synchronized(bufferLock) {
                if (!isRunning.get()) return

                if (buffering && jitterBuffer.size >= minOf(targetPackets, refillPackets)) {
                    // Cushion is full: start (or resume) playing from the oldest packet we have
                    buffering = false
                    playSeq = jitterBuffer.firstKey()
                }

                if (!buffering) {
                    if (jitterBuffer.containsKey(playSeq)) {
                        chunkToPlay = jitterBuffer.remove(playSeq)
                        lastChunk = chunkToPlay
                        concealCount = 0
                        playSeq++
                    } else {
                        val nextKey = jitterBuffer.higherKey(playSeq)
                        if (nextKey != null && nextKey - playSeq <= 3) {
                            // Small gap: repeat the last packet faded (first gap 50%, second 25%, then silence)
                            val lc = lastChunk
                            if (lc != null && concealCount < 2) {
                                val shift = concealCount + 1
                                for (i in lc.indices) concealBuf[i] = (lc[i].toInt() shr shift).toShort()
                                chunkToPlay = concealBuf
                            } else {
                                chunkToPlay = silence
                            }
                            concealCount++
                            playSeq++
                        } else if (nextKey != null) {
                            // Large gap: skip ahead
                            playSeq = nextKey
                            chunkToPlay = jitterBuffer.remove(playSeq)
                            lastChunk = chunkToPlay
                            concealCount = 0
                            playSeq++
                        } else {
                            // Underrun: go back to buffering so the cushion is rebuilt instead of
                            // running at zero depth (which would stutter on every late packet)
                            buffering = true
                            refillPackets = 4
                        }
                    }
                }
                nextPlaySeq = if (buffering) -1L else playSeq
            }

            // While buffering, keep the audio track fed with silence (the blocking write paces the loop)
            val toWrite = chunkToPlay ?: if (buffering) silence else null
            if (toWrite != null && isRunning.get()) {
                audioTrack?.write(toWrite, 0, toWrite.size)
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
