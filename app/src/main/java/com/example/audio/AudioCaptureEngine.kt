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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class AudioInputDevice(
    val id: Int,
    val name: String,
    val type: Int,
    val isUsb: Boolean,
    val isBuiltInMic: Boolean,
    val rawDevice: AudioDeviceInfo
)

/**
 * Ultra-low-latency audio monitor connecting HDMI USB capture audio (or microphone) to AudioTrack.
 * Features:
 * - Enumerates audio inputs, defaults to USB capture card
 * - Performance mode: AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
 * - Howl-round warning and auto-mute when phone microphone is chosen
 * - Acoustic anti-feedback guard: auto-mutes on sustained saturation/howling
 * - Reacts dynamically to USB audio connection/disconnection
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
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private val isRunning = AtomicBoolean(false)
    private var isMuted = false
    private var currentVolume = 1.0f // 0.0 to 1.0

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

    fun setMute(muted: Boolean) {
        isMuted = muted
    }

    fun isMuted(): Boolean = isMuted

    fun setVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
    }

    fun getVolume(): Float = currentVolume

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
            Log.e(TAG, "Failed to create AudioRecord: ${e.message}")
            onError("Audio input init failed: ${e.message}")
            return
        }

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
            Log.e(TAG, "Failed to create AudioTrack: ${e.message}")
            onError("Audio monitor init failed: ${e.message}")
            return
        }

        audioRecord = record
        audioTrack = track

        try {
            record.startRecording()
            track.play()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start AudioRecord or AudioTrack: ${e.message}")
            record.release()
            track.release()
            return
        }

        val bufferSize = 960 // ~10ms at 48kHz stereo 16-bit
        val pcmBuffer = ShortArray(bufferSize)

        Log.d(TAG, "Audio monitoring pipeline running at $SAMPLE_RATE Hz (Device: ${inputDev.name})")

        while (isRunning.get()) {
            val shortsRead = record.read(pcmBuffer, 0, pcmBuffer.size)
            if (shortsRead <= 0) continue

            // Anti-feedback guard analysis
            var maxPeak = 0
            for (i in 0 until shortsRead) {
                val absVal = abs(pcmBuffer[i].toInt())
                if (absVal > maxPeak) {
                    maxPeak = absVal
                }
            }

            if (maxPeak >= FEEDBACK_THRESHOLD) {
                feedbackTriggerCount++
                if (feedbackTriggerCount >= FEEDBACK_CONSECUTIVE_FRAMES) {
                    // Acoustic howl-round detected! Auto-mute to protect user's ears
                    Log.w(TAG, "Acoustic feedback howl-round detected! Auto-muting audio.")
                    isMuted = true
                    feedbackTriggerCount = 0
                    onFeedbackDetected()
                }
            } else {
                feedbackTriggerCount = 0
            }

            if (isMuted || currentVolume <= 0.001f) {
                // Zero out buffer so monitor is silent
                pcmBuffer.fill(0)
            } else if (currentVolume < 0.99f) {
                // Apply volume scale without clipping
                for (i in 0 until shortsRead) {
                    pcmBuffer[i] = (pcmBuffer[i] * currentVolume).toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            track.write(pcmBuffer, 0, shortsRead)
        }

        try {
            record.stop()
            record.release()
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
