package com.example.audio

import android.annotation.SuppressLint
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

    @SuppressLint("MissingPermission")
    private fun restartAudioPipeline() {
        stopAudioPipeline()

        val dev = selectedDevice ?: return
        isRunning.set(true)

        audioThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            runAudioLoop(dev)
        }, "AudioMonitorThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    @SuppressLint("MissingPermission")
    private fun runAudioLoop(inputDev: AudioInputDevice) {
        val channelIn = AudioFormat.CHANNEL_IN_STEREO
        val channelOut = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_16BIT

        val minRecBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelIn, encoding)
        val minTrackBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, channelOut, encoding)

        if (minRecBuf <= 0 || minTrackBuf <= 0) {
            Log.e(TAG, "Invalid buffer sizes for audio: rec=$minRecBuf, track=$minTrackBuf")
            return
        }

        val recBufferSize = (minRecBuf * 2).coerceAtLeast(1920)
        val trackBufferSize = (minTrackBuf * 2).coerceAtLeast(1920)

        // 1. Primary Capture AudioRecord
        val record = try {
            val builder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(channelIn)
                        .build()
                )
                .setBufferSizeInBytes(recBufferSize)

            val rec = builder.build()
            rec.setPreferredDevice(inputDev.rawDevice)
            rec
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create primary AudioRecord: ${e.message}")
            onError("Audio input init failed: ${e.message}")
            return
        }

        // 2. Secondary Mic AudioRecord (Try capturing built-in mic simultaneously)
        var secondaryMicRecord: AudioRecord? = null
        val builtInMicDevice = availableDevices.find { it.isBuiltInMic }?.rawDevice
        if (inputDev.isUsb && builtInMicDevice != null) {
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
                    Log.i(TAG, "Secondary device microphone AudioRecord opened successfully")
                } else {
                    micRec.release()
                    micAvailable = false
                    Log.w(TAG, "Secondary mic not recording, fallback to single source")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Secondary mic recording not supported on this device: ${e.message}")
                micAvailable = false
            }
        } else {
            micAvailable = !inputDev.isUsb
        }

        // 3. AudioTrack for local monitoring
        val track = try {
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
            return
        }

        audioRecord = record
        micRecord = secondaryMicRecord
        audioTrack = track

        try {
            record.startRecording()
            track.play()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start AudioRecord or AudioTrack: ${e.message}")
            record.release()
            secondaryMicRecord?.release()
            track.release()
            return
        }

        val bufferSize = 960 // 10ms at 48kHz stereo 16-bit
        val pcmBuffer = ShortArray(bufferSize)
        val micBuffer = ShortArray(bufferSize / 2)
        val playbackBuffer = ShortArray(bufferSize)

        var meterBlockCount = 0

        Log.d(TAG, "Audio monitoring pipeline running at $SAMPLE_RATE Hz (Device: ${inputDev.name})")

        while (isRunning.get()) {
            val shortsRead = record.read(pcmBuffer, 0, pcmBuffer.size)
            if (shortsRead <= 0) continue

            val now = System.currentTimeMillis()

            // 1. Calculate raw Capture peak & RMS for metering
            var capPeakShort = 0
            var capSumSquares = 0.0
            for (i in 0 until shortsRead) {
                val s = pcmBuffer[i].toInt()
                val absS = abs(s)
                if (absS > capPeakShort) capPeakShort = absS
                capSumSquares += s.toDouble() * s.toDouble()
            }

            // Anti-feedback guard analysis
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

            // Calculate Capture dBFS
            val capPeakDb = if (capPeakShort > 0) {
                (20.0 * log10(capPeakShort / 32768.0)).toFloat().coerceIn(-60f, 0f)
            } else -60f

            val capRmsDb = if (shortsRead > 0) {
                val rms = sqrt(capSumSquares / shortsRead)
                if (rms > 0) (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
            } else -60f

            // 2. Read secondary mic if open
            var micPeakDb = -60f
            var micRmsDb = -60f
            if (secondaryMicRecord != null && micAvailable) {
                val micShorts = secondaryMicRecord.read(micBuffer, 0, micBuffer.size)
                if (micShorts > 0) {
                    var mPeak = 0
                    var mSum = 0.0
                    for (i in 0 until micShorts) {
                        val s = micBuffer[i].toInt()
                        val absS = abs(s)
                        if (absS > mPeak) mPeak = absS
                        mSum += s.toDouble() * s.toDouble()
                    }
                    micPeakDb = if (mPeak > 0) (20.0 * log10(mPeak / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    micRmsDb = if (micShorts > 0) {
                        val rms = sqrt(mSum / micShorts)
                        if (rms > 0) (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-60f, 0f) else -60f
                    } else -60f
                }
            } else if (!inputDev.isUsb) {
                // If primary device is built-in mic itself
                micPeakDb = capPeakDb
                micRmsDb = capRmsDb
            }

            // 3. Publish meters at ~20 Hz (every 5 blocks of 10ms = 50ms)
            meterBlockCount++
            if (meterBlockCount >= 5) {
                meterBlockCount = 0
                onMetersCalculated?.invoke(
                    Protocol.SourceMeter(micRmsDb, micPeakDb),
                    Protocol.SourceMeter(capRmsDb, capPeakDb)
                )
            }

            // 4. Apply Capture Gain & Mute for Streamer & Playback
            val effectiveCaptureGain = if (isMuted || captureMuted) 0f else (captureGain * currentVolume)
            for (i in 0 until shortsRead) {
                if (effectiveCaptureGain <= 0.001f) {
                    pcmBuffer[i] = 0
                } else if (effectiveCaptureGain < 0.99f || effectiveCaptureGain > 1.01f) {
                    pcmBuffer[i] = (pcmBuffer[i] * effectiveCaptureGain).toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            // 5. Send to UDP AudioStreamer (Stage 5) - captures card audio only, never mic
            audioStreamer?.sendPcmChunk(pcmBuffer, shortsRead, now)

            // 6. Local Monitor Output on Feed
            if (!localMonitorEnabled || isMuted || currentVolume <= 0.001f) {
                playbackBuffer.fill(0)
            } else {
                System.arraycopy(pcmBuffer, 0, playbackBuffer, 0, shortsRead)
            }

            track.write(playbackBuffer, 0, shortsRead)
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
    }

    private fun stopAudioPipeline() {
        isRunning.set(false)
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
