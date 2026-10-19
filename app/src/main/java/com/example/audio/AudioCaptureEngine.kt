package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.net.AudioStreamer
import com.example.net.Protocol
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioInputDevice(
    val id: Int,
    val name: String,
    val type: Int,
    val isUsb: Boolean,
    val isBuiltInMic: Boolean,
    val rawDevice: AudioDeviceInfo
)

/**
 * Ultra-low-latency audio monitor connecting HDMI USB capture audio and device microphone
 * to local AudioTrack and wireless UDP AudioStreamer.
 * Features:
 * - Dual logical channels: "capture" (USB capture card) and "mic" (device mic)
 * - Separate gain and mute controls per channel
 * - Zero-allocation RMS and peak calculation in dBFS (floor -60 dBFS)
 * - Graceful fallback if secondary mic AudioRecord cannot open
 * - Local monitor toggle (mutes Feed-side speaker without affecting stream or meters)
 * - Low-latency pass-through to AudioStreamer UDP socket
 * - Acoustic anti-feedback / howl-round protection
 */
class AudioCaptureEngine(
    private val context: Context,
    private val onDevicesUpdated: (List<AudioInputDevice>, AudioInputDevice?) -> Unit,
    private val onHowlRoundWarning: () -> Unit,
    private val onFeedbackDetected: () -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureEngine"
        private const val SAMPLE_RATE = 48000 // Standard HDMI capture card sample rate
        private const val FEEDBACK_THRESHOLD = 32000
        private const val FEEDBACK_CONSECUTIVE_FRAMES = 12
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private val isRunning = AtomicBoolean(false)

    // Master volume & mute (Feed UI)
    private var isMuted = false
    private var currentVolume = 1.0f // 0.0 to 1.0

    // Dual channel controls
    @Volatile
    private var captureGain = 1.0f
    @Volatile
    private var captureMuted = false

    @Volatile
    private var micGain = 1.0f
    @Volatile
    private var micMuted = false

    @Volatile
    private var micAvailable = true

    @Volatile
    private var localMonitorEnabled = true

    // External listener for 20 Hz meters & UDP audio streamer tap
    var onMetersCalculated: ((mic: Protocol.SourceMeter, capture: Protocol.SourceMeter) -> Unit)? = null
    var audioStreamer: AudioStreamer? = null

    // Human-readable live status for the Feed HUD (device, route, level, mute flags)
    var onStatus: ((String) -> Unit)? = null

    // Generation counter: lets a restart retire any older audio thread that is still winding down
    @Volatile private var generation = 0

    private var selectedDevice: AudioInputDevice? = null
    private var availableDevices: List<AudioInputDevice> = emptyList()

    private var feedbackTriggerCount = 0

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices added: ${addedDevices?.size}")
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            Log.d(TAG, "Audio devices removed: ${removedDevices?.size}")
            refreshDevices()
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        refreshDevices()
        ensureRunning()
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Starts the pipeline if it is not running. Needed because the pipeline is otherwise only
     * (re)started when the selected device CHANGES, so a start attempt made before the
     * microphone permission was granted, or after leaving and re-entering Feed mode, was never retried.
     */
    fun ensureRunning() {
        if (!hasRecordPermission()) {
            onStatus?.invoke("AUDIO: waiting for microphone permission")
            return
        }
        if (selectedDevice == null) {
            refreshDevices()
            if (selectedDevice == null) {
                onStatus?.invoke("AUDIO: no audio input device found")
                return
            }
        }
        val t = audioThread
        if (t == null || !t.isAlive) restartAudioPipeline()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        stopAudioPipeline()
    }

    fun refreshDevices() {
        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val devices = rawInputs.map { dev ->
            val isUsb = dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
            val isBuiltIn = dev.type == AudioDeviceInfo.TYPE_BUILTIN_MIC

            val name = when {
                isUsb -> {
                    val prod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dev.productName?.toString()?.trim()
                    } else null
                    if (!prod.isNullOrEmpty()) "USB: $prod" else "USB Capture Audio"
                }
                isBuiltIn -> "Phone Microphone (Built-in)"
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Headset Microphone"
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Audio"
                else -> {
                    val prod = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        dev.productName?.toString()?.trim()
                    } else null
                    prod ?: "Audio Input (${dev.id})"
                }
            }

            AudioInputDevice(
                id = dev.id,
                name = name,
                type = dev.type,
                isUsb = isUsb,
                isBuiltInMic = isBuiltIn,
                rawDevice = dev
            )
        }

        availableDevices = devices

        // Default to USB device if available
        val usbDevice = devices.find { it.isUsb }
        val current = selectedDevice

        val newSelection = when {
            // Keep current if still connected
            current != null && devices.any { it.id == current.id } -> {
                devices.first { it.id == current.id }
            }
            // Auto switch to USB when attached
            usbDevice != null -> usbDevice
            // Fall back to first available or built-in mic
            else -> devices.find { it.isBuiltInMic } ?: devices.firstOrNull()
        }

        val selectionChanged = selectedDevice?.id != newSelection?.id
        selectedDevice = newSelection

        onDevicesUpdated(availableDevices, selectedDevice)

        if (selectionChanged && selectedDevice != null) {
            selectDevice(selectedDevice!!, isAutoSwitch = true)
        }
    }

    fun selectDevice(device: AudioInputDevice, isAutoSwitch: Boolean = false) {
        selectedDevice = device

        if (device.isBuiltInMic) {
            // Howl-round warning: phone mic near speaker can create acoustic feedback
            isMuted = true
            onHowlRoundWarning()
        }

        restartAudioPipeline()
    }

    // Master controls
    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
    }

    fun getVolume(): Float = currentVolume

    // Dual channel gain & mute controls
    fun setCaptureGain(gain: Float) {
        captureGain = gain.coerceIn(0f, 1f)
    }

    fun getCaptureGain(): Float = captureGain

    fun setCaptureMuted(muted: Boolean) {
        captureMuted = muted
    }

    fun isCaptureMuted(): Boolean = captureMuted

    fun setMicGain(gain: Float) {
        micGain = gain.coerceIn(0f, 1f)
    }

    fun getMicGain(): Float = micGain

    fun setMicMuted(muted: Boolean) {
        micMuted = muted
    }

    fun isMicMuted(): Boolean = micMuted

    fun isMicAvailable(): Boolean = micAvailable

    fun setLocalMonitorEnabled(enabled: Boolean) {
        localMonitorEnabled = enabled
        Log.i(TAG, "Local monitor enabled set to: $enabled")
    }

    fun isLocalMonitorEnabled(): Boolean = localMonitorEnabled

    @Synchronized
    private fun restartAudioPipeline() {
        stopAudioPipeline()

        val dev = selectedDevice ?: return
        if (!hasRecordPermission()) {
            onStatus?.invoke("AUDIO: waiting for microphone permission")
            return
        }
        isRunning.set(true)
        val gen = ++generation

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop(dev, gen)
        }, "AudioMonitorThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun alive(gen: Int) = isRunning.get() && gen == generation

    private fun routeName(info: AudioDeviceInfo?): String = when (info?.type) {
        null -> "unknown"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "built-in mic"
        else -> "type ${info?.type}"
    }

    /**
     * Tries several capture configurations for the selected device until one actually delivers data.
     */
    private fun runAudioLoop(inputDev: AudioInputDevice, gen: Int) {
        val configs = listOf(
            MediaRecorder.AudioSource.UNPROCESSED to AudioFormat.CHANNEL_IN_STEREO,
            MediaRecorder.AudioSource.UNPROCESSED to AudioFormat.CHANNEL_IN_MONO,
            MediaRecorder.AudioSource.MIC to AudioFormat.CHANNEL_IN_STEREO,
            MediaRecorder.AudioSource.MIC to AudioFormat.CHANNEL_IN_MONO
        )
        for ((index, cfg) in configs.withIndex()) {
            if (!alive(gen)) return
            val finishedNormally = runWithConfig(inputDev, gen, cfg.first, cfg.second, index + 1, configs.size)
            if (finishedNormally) return
        }
        if (alive(gen)) {
            val msg = "Audio: no working input mode for ${inputDev.name}"
            onStatus?.invoke("AUDIO FAILED: ${inputDev.name} (tried ${configs.size} modes)")
            onError(msg)
        }
    }

    /**
     * @return true if the loop ended because it was stopped, false if this configuration failed.
     */
    @SuppressLint("MissingPermission")
    private fun runWithConfig(
        inputDev: AudioInputDevice,
        gen: Int,
        source: Int,
        channelIn: Int,
        attempt: Int,
        totalAttempts: Int
    ): Boolean {
        val inputMono = channelIn == AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val modeLabel = if (inputMono) "mono" else "stereo"

        val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelIn, encoding)
        val minTrackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelOut, encoding)
        if (minRecBuf <= 0 || minTrackBuf <= 0) {
            Log.e(TAG, "Invalid buffer sizes for audio: rec=$minRecBuf, track=$minTrackBuf ($modeLabel)")
            return false
        }
        val recBufferSize = (minRecBuf * 2).coerceAtLeast(1920 * 2)
        val trackBufferSize = (minTrackBuf * 2).coerceAtLeast(1920)

        // 1. Primary capture AudioRecord, routed to the selected device
        val record: AudioRecord = try {
            val rec = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelIn)
                        .build()
                )
                .setBufferSizeInBytes(recBufferSize)
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                Log.w(TAG, "AudioRecord not initialised ($modeLabel, source $source)")
                return false
            }
            val prefOk = rec.setPreferredDevice(inputDev.rawDevice)
            Log.i(TAG, "setPreferredDevice(${inputDev.name}) -> $prefOk ($modeLabel, source $source)")
            rec
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create primary AudioRecord ($modeLabel): ${e.message}")
            return false
        }

        // 2. Secondary mic AudioRecord (built-in mic alongside USB capture), best effort
        var secondaryMicRecord: AudioRecord? = null
        val builtInMicDevice = availableDevices.find { it.isBuiltInMic }?.rawDevice
        if (false && inputDev.isUsb && builtInMicDevice != null) { // secondary VOICE_COMMUNICATION mic disabled: it put the audio HAL in call mode
            try {
                val micRec = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(encoding)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(recBufferSize)
                    .build()
                micRec.setPreferredDevice(builtInMicDevice)
                micRec.startRecording()
                if (micRec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    secondaryMicRecord = micRec
                    micAvailable = true
                } else {
                    micRec.release()
                    micAvailable = false
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Secondary mic recording not supported on this device: ${e.message}")
                micAvailable = false
            }
        } else {
            micAvailable = !inputDev.isUsb
        }

        // 3. AudioTrack for local monitoring on the Feed
        val track: AudioTrack = try {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(encoding)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(channelOut)
                .build()
            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(attr)
                .setAudioFormat(format)
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            trackBuilder.build()
        } catch (e: Throwable) {
            record.release()
            secondaryMicRecord?.release()
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            onError("Audio monitor init failed: ${e.message}")
            return false
        }

        audioRecord = record
        micRecord = secondaryMicRecord
        audioTrack = track

        try {
            record.startRecording()
            track.play()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start AudioRecord or AudioTrack ($modeLabel): ${e.message}")
            record.release()
            secondaryMicRecord?.release()
            track.release()
            return false
        }

        val route = routeName(record.routedDevice)
        Log.i(TAG, "Audio running: ${inputDev.name}, $modeLabel, source=$source, routed to $route (attempt $attempt/$totalAttempts)")

        val frameShorts = 960 // 10 ms at 48 kHz stereo
        val pcmBuffer = ShortArray(frameShorts)
        val monoBuffer = ShortArray(frameShorts / 2)
        val micBuffer = ShortArray(frameShorts / 2)
        val playbackBuffer = ShortArray(frameShorts)

        var meterBlockCount = 0
        var lastMicPeakDb = -60f
        var lastMicRmsDb = -60f
        var statusBlockCount = 0
        var errorStreak = 0
        var silentBlocks = 0
        var failed = false
        val startTime = System.currentTimeMillis()

        while (alive(gen)) {
            // Blocking read of exactly one 10 ms block
            val shortsRead: Int
            if (inputMono) {
                val n = record.read(monoBuffer, 0, monoBuffer.size, AudioRecord.READ_BLOCKING)
                if (n > 0) {
                    for (i in 0 until n) {
                        pcmBuffer[2 * i] = monoBuffer[i]
                        pcmBuffer[2 * i + 1] = monoBuffer[i]
                    }
                }
                shortsRead = if (n > 0) n * 2 else n
            } else {
                shortsRead = record.read(pcmBuffer, 0, pcmBuffer.size, AudioRecord.READ_BLOCKING)
            }

            if (shortsRead <= 0) {
                errorStreak++
                if (errorStreak == 1) Log.w(TAG, "AudioRecord.read returned $shortsRead ($modeLabel)")
                if (errorStreak >= 50) {
                    Log.e(TAG, "AudioRecord keeps failing (last code $shortsRead), giving up on this mode")
                    failed = true
                    break
                }
                try { Thread.sleep(5) } catch (_: InterruptedException) { }
                continue
            }
            errorStreak = 0

            val now = System.currentTimeMillis()

            // Capture peak & RMS (pre-gain) for the meters
            var capPeakShort = 0
            var capSumSquares = 0.0
            for (i in 0 until shortsRead) {
                val sample = pcmBuffer[i].toInt()
                val absS = abs(sample)
                if (absS > capPeakShort) capPeakShort = absS
                capSumSquares += sample.toDouble() * sample.toDouble()
            }
            if (capPeakShort == 0) silentBlocks++ else silentBlocks = 0

            // Anti-feedback guard
            if (capPeakShort >= FEEDBACK_THRESHOLD) {
                feedbackTriggerCount++
                if (feedbackTriggerCount >= FEEDBACK_CONSECUTIVE_FRAMES) {
                    Log.w(TAG, "Acoustic feedback howl-round detected! Auto-muting audio.")
                    isMuted = true
                    feedbackTriggerCount = 0
                    onFeedbackDetected()
                }
            } else {
                feedbackTriggerCount = 0
            }

            val capPeakDb = if (capPeakShort > 0) {
                (20.0 * log10(capPeakShort / 32768.0)).toFloat().coerceIn(-60f, 0f)
            } else -60f
            val capRmsDb = run {
                val rms = sqrt(capSumSquares / shortsRead)
                if (rms > 0) (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
            }

            // Secondary mic
            var micPeakDb = lastMicPeakDb
            var micRmsDb = lastMicRmsDb
            if (secondaryMicRecord != null && micAvailable) {
                // Non-blocking: the capture card read must be the ONLY thing pacing this loop,
                // otherwise the mic's separate clock makes the stream packets bursty
                val micShorts = secondaryMicRecord.read(micBuffer, 0, micBuffer.size, AudioRecord.READ_NON_BLOCKING)
                if (micShorts > 0) {
                    var mPeak = 0
                    var mSum = 0.0
                    for (i in 0 until micShorts) {
                        val sample = micBuffer[i].toInt()
                        val absS = abs(sample)
                        if (absS > mPeak) mPeak = absS
                        mSum += sample.toDouble() * sample.toDouble()
                    }
                    micPeakDb = if (mPeak > 0) (20.0 * log10(mPeak / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    val rms = sqrt(mSum / micShorts)
                    micRmsDb = if (rms > 0) (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                }
            } else if (!inputDev.isUsb) {
                micPeakDb = capPeakDb
                micRmsDb = capRmsDb
            }

            lastMicPeakDb = micPeakDb
            lastMicRmsDb = micRmsDb

            // Meters at ~20 Hz
            meterBlockCount++
            if (meterBlockCount >= 5) {
                meterBlockCount = 0
                onMetersCalculated?.invoke(
                    Protocol.SourceMeter(micRmsDb, micPeakDb),
                    Protocol.SourceMeter(capRmsDb, capPeakDb)
                )
            }

            // HUD status ~2 Hz
            statusBlockCount++
            if (statusBlockCount >= 50) {
                statusBlockCount = 0
                val flags = StringBuilder()
                if (silentBlocks >= 300) flags.append(" | NO SIGNAL (digital silence)")
                if (isMuted) flags.append(" | MUTED(master)")
                if (captureMuted) flags.append(" | MUTED(capture)")
                if (!localMonitorEnabled) flags.append(" | local monitor off")
                onStatus?.invoke(
                    "AUDIO ${inputDev.name} | $modeLabel | route: ${routeName(record.routedDevice)} | " +
                        String.format("%.0f dB", capPeakDb) + flags
                )
            }

            // Capture gain & mute for stream and local playback
            val effectiveCaptureGain = if (isMuted || captureMuted) 0f else (captureGain * currentVolume)
            for (i in 0 until shortsRead) {
                if (effectiveCaptureGain <= 0.001f) {
                    pcmBuffer[i] = 0
                } else if (effectiveCaptureGain < 0.99f || effectiveCaptureGain > 1.01f) {
                    pcmBuffer[i] = (pcmBuffer[i] * effectiveCaptureGain).toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            // UDP stream to the Remote (capture card audio only, never the mic)
            audioStreamer?.sendPcmChunk(pcmBuffer, shortsRead, now)

            // Local monitor on the Feed. Non-blocking, and skipped entirely when off, so the tablet's
            // own audio clock never paces (and so never disturbs) the stream sent to the Remote.
            if (localMonitorEnabled && !isMuted && currentVolume > 0.001f) {
                System.arraycopy(pcmBuffer, 0, playbackBuffer, 0, shortsRead)
                track.write(playbackBuffer, 0, shortsRead, AudioTrack.WRITE_NON_BLOCKING)
            }
        }

        try {
            record.stop()
            record.release()
            secondaryMicRecord?.stop()
            secondaryMicRecord?.release()
            track.stop()
            track.release()
        } catch (e: Throwable) {
            Log.w(TAG, "Error cleaning up audio: ${e.message}")
        }

        return !failed
    }

    private fun stopAudioPipeline() {
        isRunning.set(false)
        generation++
        try {
            audioThread?.join(300)
        } catch (_: InterruptedException) {
        }
        audioThread = null
    }

    fun release() {
        stop()
    }
}
