package com.example.ui

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioCaptureEngine
import com.example.audio.AudioInputDevice
import com.example.capture.QualityMode
import com.example.capture.UsbMonitorManager
import com.example.capture.UvcCaptureEngine
import com.example.player.MjpegPlayer
import com.example.player.PlayerStats
import com.example.player.ScaleMode
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CurrentScreen {
    CONNECT,
    PLAYER
}

data class UiState(
    val currentScreen: CurrentScreen = CurrentScreen.CONNECT,
    val attachedDevice: UsbDevice? = null,
    val hasUsbPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val supportedQualityModes: List<QualityMode> = emptyList(),
    val currentQualityMode: QualityMode = QualityMode.DEFAULT_720P_60,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val isStatsOverlayVisible: Boolean = true,
    val playerStats: PlayerStats = PlayerStats(),
    val availableAudioDevices: List<AudioInputDevice> = emptyList(),
    val selectedAudioDevice: AudioInputDevice? = null,
    val audioVolume: Float = 1.0f,
    val isAudioMuted: Boolean = false,
    val feedbackWarningVisible: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "capture_studio_prefs"
        private const val KEY_QUALITY_MODE = "pref_quality_mode"
        private const val KEY_SCALE_MODE = "pref_scale_mode"
        private const val KEY_AUDIO_VOLUME = "pref_audio_volume"
        private const val KEY_AUDIO_MUTED = "pref_audio_muted"
        private const val KEY_STATS_OVERLAY = "pref_stats_overlay"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Low-level engines
    val mjpegPlayer = MjpegPlayer { stats ->
        _uiState.update { it.copy(playerStats = stats) }
    }

    private var activeCtrlBlock: USBMonitor.UsbControlBlock? = null

    val uvcCaptureEngine = UvcCaptureEngine(
        mjpegPlayer = mjpegPlayer,
        onStreamConfigured = { reqFps, negFps, width, height ->
            _uiState.update { current ->
                current.copy(
                    playerStats = current.playerStats.copy(
                        requestedFps = reqFps,
                        negotiatedFps = negFps
                    )
                )
            }
        },
        onError = { msg ->
            _uiState.update { it.copy(errorMessage = msg) }
        }
    )

    val audioCaptureEngine = AudioCaptureEngine(
        context = application,
        onDevicesUpdated = { devices, selected ->
            _uiState.update {
                it.copy(
                    availableAudioDevices = devices,
                    selectedAudioDevice = selected
                )
            }
        },
        onHowlRoundWarning = {
            _uiState.update { it.copy(isAudioMuted = true) }
        },
        onFeedbackDetected = {
            _uiState.update {
                it.copy(
                    isAudioMuted = true,
                    feedbackWarningVisible = true
                )
            }
        },
        onError = { msg ->
            Log.e(TAG, "Audio error: $msg")
        }
    )

    private val usbMonitorManager = UsbMonitorManager(
        context = application,
        onDeviceAttached = { device ->
            onUsbDeviceAttached(device)
        },
        onDeviceDetached = { device ->
            onUsbDeviceDetached(device)
        },
        onDeviceOpened = { device, ctrlBlock ->
            onUsbDeviceOpened(device, ctrlBlock)
        },
        onDeviceClosed = { device ->
            onUsbDeviceClosed(device)
        },
        onPermissionDenied = { device ->
            _uiState.update { it.copy(hasUsbPermission = false) }
        }
    )

    init {
        restoreSettings()
        usbMonitorManager.start()
        audioCaptureEngine.start()
        mjpegPlayer.start()
        checkInitialUsbDevices()
    }

    private fun restoreSettings() {
        val savedQualityId = prefs.getString(KEY_QUALITY_MODE, QualityMode.ID_720P_60) ?: QualityMode.ID_720P_60
        val savedScale = prefs.getString(KEY_SCALE_MODE, ScaleMode.FIT.name)
        val scale = try {
            ScaleMode.valueOf(savedScale ?: ScaleMode.FIT.name)
        } catch (_: Throwable) {
            ScaleMode.FIT
        }
        val savedVolume = prefs.getFloat(KEY_AUDIO_VOLUME, 1.0f)
        val savedMuted = prefs.getBoolean(KEY_AUDIO_MUTED, false)
        val savedStats = prefs.getBoolean(KEY_STATS_OVERLAY, true)

        mjpegPlayer.setScaleMode(scale)
        audioCaptureEngine.setVolume(savedVolume)
        audioCaptureEngine.setMute(savedMuted)

        val initialMode = when (savedQualityId) {
            QualityMode.ID_720P_30 -> QualityMode.DEFAULT_720P_30
            QualityMode.ID_480P_60 -> QualityMode.DEFAULT_480P_60
            QualityMode.ID_480P_30 -> QualityMode.DEFAULT_480P_30
            QualityMode.ID_1080P_30 -> QualityMode.DEFAULT_1080P_30
            else -> QualityMode.DEFAULT_720P_60
        }

        _uiState.update {
            it.copy(
                currentQualityMode = initialMode,
                scaleMode = scale,
                audioVolume = savedVolume,
                isAudioMuted = savedMuted,
                isStatsOverlayVisible = savedStats
            )
        }
    }

    fun setSystemPermissions(hasCamera: Boolean, hasAudio: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = hasCamera,
                hasAudioPermission = hasAudio
            )
        }
        if (hasCamera) {
            checkInitialUsbDevices()
        }
    }

    private fun checkInitialUsbDevices() {
        val devices = usbMonitorManager.getConnectedUvcDevices()
        if (devices.isNotEmpty()) {
            val device = devices.first()
            val hasPerm = usbMonitorManager.hasPermission(device)
            _uiState.update {
                it.copy(
                    attachedDevice = device,
                    hasUsbPermission = hasPerm
                )
            }
            if (hasPerm) {
                // If permitted, start the live full-screen player automatically!
                usbMonitorManager.requestPermission(device)
            }
        }
    }

    private fun onUsbDeviceAttached(device: UsbDevice) {
        val hasPerm = usbMonitorManager.hasPermission(device)
        _uiState.update {
            it.copy(
                attachedDevice = device,
                hasUsbPermission = hasPerm
            )
        }
        if (hasPerm) {
            usbMonitorManager.requestPermission(device)
        }
        audioCaptureEngine.refreshDevices()
    }

    private fun onUsbDeviceDetached(device: UsbDevice) {
        if (_uiState.value.attachedDevice?.deviceId == device.deviceId) {
            uvcCaptureEngine.close()
            activeCtrlBlock = null
            _uiState.update {
                it.copy(
                    attachedDevice = null,
                    hasUsbPermission = false,
                    currentScreen = CurrentScreen.CONNECT,
                    supportedQualityModes = emptyList()
                )
            }
        }
        audioCaptureEngine.refreshDevices()
    }

    private fun onUsbDeviceOpened(device: UsbDevice, ctrlBlock: USBMonitor.UsbControlBlock) {
        activeCtrlBlock = ctrlBlock
        _uiState.update {
            it.copy(
                attachedDevice = device,
                hasUsbPermission = true
            )
        }

        // Open UvcCaptureEngine and discover modes
        val modes = uvcCaptureEngine.open(ctrlBlock)
        _uiState.update { it.copy(supportedQualityModes = modes) }

        // Determine best mode (restore saved preference if supported, otherwise best supported)
        val savedQualityId = prefs.getString(KEY_QUALITY_MODE, QualityMode.ID_720P_60)
        val selected = modes.find { it.id == savedQualityId && it.isSupportedByCard }
            ?: modes.find { it.id == QualityMode.ID_720P_60 && it.isSupportedByCard }
            ?: modes.find { it.isSupportedByCard }
            ?: modes.firstOrNull()
            ?: QualityMode.DEFAULT_720P_60

        _uiState.update {
            it.copy(
                currentQualityMode = selected,
                currentScreen = CurrentScreen.PLAYER
            )
        }

        // Start video stream
        uvcCaptureEngine.startStream(selected)
    }

    private fun onUsbDeviceClosed(device: UsbDevice) {
        if (_uiState.value.attachedDevice?.deviceId == device.deviceId) {
            uvcCaptureEngine.close()
            activeCtrlBlock = null
            _uiState.update {
                it.copy(currentScreen = CurrentScreen.CONNECT)
            }
        }
    }

    fun requestUsbPermission() {
        val device = _uiState.value.attachedDevice
        if (device != null) {
            usbMonitorManager.requestPermission(device)
        } else {
            val detected = usbMonitorManager.getConnectedUvcDevices()
            if (detected.isNotEmpty()) {
                val dev = detected.first()
                _uiState.update { it.copy(attachedDevice = dev) }
                usbMonitorManager.requestPermission(dev)
            } else {
                _uiState.update {
                    it.copy(errorMessage = "No USB capture device found. Please connect HDMI capture card.")
                }
            }
        }
    }

    fun startPlayerManually() {
        val block = activeCtrlBlock
        if (block != null) {
            val mode = _uiState.value.currentQualityMode
            uvcCaptureEngine.startStream(mode)
            _uiState.update { it.copy(currentScreen = CurrentScreen.PLAYER) }
        } else {
            requestUsbPermission()
        }
    }

    fun selectQualityMode(mode: QualityMode) {
        if (!mode.isSupportedByCard) return
        _uiState.update { it.copy(currentQualityMode = mode) }
        prefs.edit().putString(KEY_QUALITY_MODE, mode.id).apply()

        // Changing quality must restart the stream cleanly without restarting the app
        uvcCaptureEngine.restartStreamWithMode(mode)
    }

    fun toggleScaleMode() {
        val newScale = if (_uiState.value.scaleMode == ScaleMode.FIT) ScaleMode.FILL else ScaleMode.FIT
        mjpegPlayer.setScaleMode(newScale)
        _uiState.update { it.copy(scaleMode = newScale) }
        prefs.edit().putString(KEY_SCALE_MODE, newScale.name).apply()
    }

    fun toggleStatsOverlay() {
        val newVisibility = !_uiState.value.isStatsOverlayVisible
        _uiState.update { it.copy(isStatsOverlayVisible = newVisibility) }
        prefs.edit().putBoolean(KEY_STATS_OVERLAY, newVisibility).apply()
    }

    fun selectAudioDevice(device: AudioInputDevice) {
        audioCaptureEngine.selectDevice(device)
        _uiState.update {
            it.copy(
                selectedAudioDevice = device,
                isAudioMuted = audioCaptureEngine.isMuted()
            )
        }
    }

    fun setAudioVolume(volume: Float) {
        audioCaptureEngine.setVolume(volume)
        _uiState.update { it.copy(audioVolume = volume) }
        prefs.edit().putFloat(KEY_AUDIO_VOLUME, volume).apply()
    }

    fun toggleAudioMute() {
        val newMute = !_uiState.value.isAudioMuted
        audioCaptureEngine.setMute(newMute)
        _uiState.update { it.copy(isAudioMuted = newMute) }
        prefs.edit().putBoolean(KEY_AUDIO_MUTED, newMute).apply()
    }

    fun dismissFeedbackWarning() {
        _uiState.update { it.copy(feedbackWarningVisible = false) }
    }

    fun navigateToConnect() {
        _uiState.update { it.copy(currentScreen = CurrentScreen.CONNECT) }
    }

    override fun onCleared() {
        super.onCleared()
        uvcCaptureEngine.close()
        usbMonitorManager.destroy()
        audioCaptureEngine.release()
        mjpegPlayer.release()
    }
}
