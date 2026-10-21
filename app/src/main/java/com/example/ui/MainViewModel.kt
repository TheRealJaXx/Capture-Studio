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
import com.example.net.AudioStreamer
import com.example.net.ButtonActionRegistry
import com.example.net.ConnectionStatus
import com.example.net.ControlClient
import com.example.net.ControlClientListener
import com.example.net.ControlServer
import com.example.net.ControlServerListener
import com.example.net.DiscoveredFeed
import com.example.net.Protocol
import com.example.net.RemoteAudioPlayer
import com.example.net.RemoteAudioService
import com.example.net.RemoteAudioStats
import com.example.player.MjpegPlayer
import com.example.player.PlayerStats
import com.example.player.ScaleMode
import com.serenegiant.usb.USBMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress

enum class CurrentScreen {
    EULA,
    START,
    CONNECT,
    PLAYER,
    REMOTE
}

data class UiState(
    val currentScreen: CurrentScreen = CurrentScreen.START,
    // Feed State
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
    val errorMessage: String? = null,
    val overlayUri: String? = null,
    val overlayVisible: Boolean = false,
    val overlayCx: Float = 0.5f,
    val overlayCy: Float = 0.5f,
    val overlayWidth: Float = 0.3f,
    val cameraLayout: CameraLayout = CameraLayout.OFF,
    val cameraFront: Boolean = false,
    val cameraPipSize: CameraPipSize = CameraPipSize.LARGE,
    val cameraAspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    val cameraBorder: Boolean = true,
    val isCameraOnly: Boolean = false,

    // Wireless Feed Server State
    val feedServerIp: String = "127.0.0.1",
    val feedPin: String = "1234",
    val isRemoteConnectedToFeed: Boolean = false,
    val feedState: Protocol.FeedState = Protocol.FeedState(),

    // Remote Mode State
    val remoteConnectionStatus: ConnectionStatus = ConnectionStatus.SEARCHING,
    val discoveredFeeds: List<DiscoveredFeed> = emptyList(),
    val connectedFeedIp: String? = null,
    val remoteLatencyMs: Long = 0,
    val remoteMicMeter: Protocol.SourceMeter = Protocol.SourceMeter.SILENCE,
    val remoteCaptureMeter: Protocol.SourceMeter = Protocol.SourceMeter.SILENCE,
    val isRemoteListening: Boolean = false,
    val remoteMonitorVolume: Float = 1.0f,
    val remoteAudioStats: RemoteAudioStats = RemoteAudioStats()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "MainViewModel"
        private const val PREFS_NAME = "capture_studio_prefs"
        private const val KEY_LAST_MODE = "pref_last_mode"
        private const val KEY_QUALITY_MODE = "pref_quality_mode"
        private const val KEY_SCALE_MODE = "pref_scale_mode"
        private const val KEY_AUDIO_VOLUME = "pref_audio_volume"
        private const val KEY_AUDIO_MUTED = "pref_audio_muted"
        private const val KEY_STATS_OVERLAY = "pref_stats_overlay"
        private const val KEY_PAIRING_PIN = "pref_pairing_pin"
        private const val KEY_SAVED_TOKEN = "pref_saved_token"
        private const val KEY_REMOTE_LISTEN = "pref_remote_listen"
        private const val KEY_REMOTE_VOL = "pref_remote_vol"
        private const val KEY_LAST_FEED_IP = "pref_last_feed_ip"
        private const val KEY_CAMERA_LAYOUT = "pref_camera_layout"
        private const val KEY_CAMERA_FRONT = "pref_camera_front"
        private const val KEY_CAMERA_PIP_SIZE = "pref_camera_pip_size"
        private const val KEY_CAMERA_ASPECT_RATIO = "pref_camera_aspect_ratio"
        private const val KEY_CAMERA_BORDER = "pref_camera_border"
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        UiState(
            currentScreen = if (isEulaAccepted(application)) CurrentScreen.START else CurrentScreen.EULA
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Low-level engines for Feed
    val mjpegPlayer = MjpegPlayer { stats ->
        _uiState.update { it.copy(playerStats = stats.copy(audioStatus = it.playerStats.audioStatus)) }
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
            _uiState.update { it.copy(errorMessage = msg) }
        }
    )

    private val usbMonitorManager = UsbMonitorManager(
        context = application,
        onDeviceAttached = { device -> onUsbDeviceAttached(device) },
        onDeviceDetached = { device -> onUsbDeviceDetached(device) },
        onDeviceOpened = { device, ctrlBlock -> onUsbDeviceOpened(device, ctrlBlock) },
        onDeviceClosed = { device -> onUsbDeviceClosed(device) },
        onPermissionDenied = { _uiState.update { it.copy(hasUsbPermission = false) } }
    )

    // Extensible Button Action Registry on Feed mapped to ShortcutConfig
    val buttonActionRegistry = ButtonActionRegistry().apply {
        registerAction(com.example.net.ShortcutConfig.ACTION_MUTE_MIC) {
            feedSetMicMute(!audioCaptureEngine.isMicMuted())
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_LISTEN_MONITOR) {
            feedToggleLocalMonitor()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_PNG_OVERLAY) {
            toggleOverlay()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_FIT_FILL) {
            toggleScaleMode()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_CAMERA_PIP) {
            cycleCameraLayout()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_FLIP_CAMERA) {
            flipCamera()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_PIP_SIZE) {
            cycleCameraPipSize()
        }
        registerAction(com.example.net.ShortcutConfig.ACTION_ASPECT_RATIO) {
            cycleCameraAspectRatio()
        }
    }

    // Wireless Feed Control Server & UDP Audio Streamer
    private val audioStreamer = AudioStreamer(application)
    private var controlServer: ControlServer? = null

    // Wireless Remote Client & Player
    private var controlClient: ControlClient? = null
    private var remoteAudioPlayer: RemoteAudioPlayer? = null

    init {
        restoreSettings()
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
        val savedPin = prefs.getString(KEY_PAIRING_PIN, "1234") ?: "1234"
        val savedRemoteVol = prefs.getFloat(KEY_REMOTE_VOL, 1.0f)
        val savedRemoteListen = prefs.getBoolean(KEY_REMOTE_LISTEN, false)
        val lastMode = prefs.getString(KEY_LAST_MODE, null)
        val savedLayoutOrdinal = prefs.getInt(KEY_CAMERA_LAYOUT, CameraLayout.OFF.ordinal)
        val savedCameraLayout = CameraLayout.values().getOrElse(savedLayoutOrdinal) { CameraLayout.OFF }
        val savedCameraFront = prefs.getBoolean(KEY_CAMERA_FRONT, false)
        val savedPipSizeOrdinal = prefs.getInt(KEY_CAMERA_PIP_SIZE, CameraPipSize.LARGE.ordinal)
        val savedCameraPipSize = CameraPipSize.values().getOrElse(savedPipSizeOrdinal) { CameraPipSize.LARGE }
        val savedAspectRatioOrdinal = prefs.getInt(KEY_CAMERA_ASPECT_RATIO, CameraAspectRatio.RATIO_4_3.ordinal)
        val savedCameraAspectRatio = CameraAspectRatio.values().getOrElse(savedAspectRatioOrdinal) { CameraAspectRatio.RATIO_4_3 }
        val savedCameraBorder = prefs.getBoolean(KEY_CAMERA_BORDER, true)

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

        val initialScreen = when (lastMode) {
            "FEED", "CAMERA_ONLY" -> CurrentScreen.PLAYER
            "REMOTE" -> CurrentScreen.REMOTE
            else -> CurrentScreen.START
        }

        _uiState.update {
            it.copy(
                currentScreen = initialScreen,
                currentQualityMode = initialMode,
                scaleMode = scale,
                audioVolume = savedVolume,
                isAudioMuted = savedMuted,
                isStatsOverlayVisible = savedStats,
                feedPin = savedPin,
                remoteMonitorVolume = savedRemoteVol,
                isRemoteListening = savedRemoteListen,
                connectedFeedIp = prefs.getString(KEY_LAST_FEED_IP, null),
                overlayUri = prefs.getString("pref_overlay_uri", null),
                overlayVisible = prefs.getBoolean("pref_overlay_visible", false),
                overlayCx = prefs.getFloat("pref_overlay_cx", 0.5f),
                overlayCy = prefs.getFloat("pref_overlay_cy", 0.5f),
                overlayWidth = prefs.getFloat("pref_overlay_w", 0.3f),
                cameraLayout = savedCameraLayout,
                cameraFront = savedCameraFront,
                cameraPipSize = savedCameraPipSize,
                cameraAspectRatio = savedCameraAspectRatio,
                cameraBorder = savedCameraBorder,
                isCameraOnly = false
            )
        }

        if (initialScreen == CurrentScreen.CONNECT || initialScreen == CurrentScreen.PLAYER) {
            startFeedSubsystems()
        } else if (initialScreen == CurrentScreen.REMOTE) {
            startRemoteSubsystems()
        }
    }

    // Mode Transitions
    fun selectFeedMode() {
        stopRemoteSubsystems()
        prefs.edit().putString(KEY_LAST_MODE, "FEED").apply()
        _uiState.update {
            it.copy(
                currentScreen = CurrentScreen.PLAYER,
                isCameraOnly = false
            )
        }
        startFeedSubsystems()
    }

    fun selectCameraOnlyMode() {
        selectFeedMode()
    }

    fun switchToCaptureMode() {
        _uiState.update { it.copy(isCameraOnly = false) }
        prefs.edit().putString(KEY_LAST_MODE, "FEED").apply()
        val dev = _uiState.value.attachedDevice
        if (dev != null && _uiState.value.hasUsbPermission) {
            val block = activeCtrlBlock
            if (block != null) {
                uvcCaptureEngine.startStream(_uiState.value.currentQualityMode)
            } else {
                usbMonitorManager.requestPermission(dev)
            }
        } else if (dev != null) {
            usbMonitorManager.requestPermission(dev)
        }
    }

    fun selectRemoteMode() {
        stopFeedSubsystems()
        prefs.edit().putString(KEY_LAST_MODE, "REMOTE").apply()
        _uiState.update {
            it.copy(
                currentScreen = CurrentScreen.REMOTE,
                isCameraOnly = false
            )
        }
        startRemoteSubsystems()
    }

    fun navigateToStart() {
        stopFeedSubsystems()
        stopRemoteSubsystems()
        prefs.edit().remove(KEY_LAST_MODE).apply()
        _uiState.update {
            it.copy(
                currentScreen = CurrentScreen.START,
                isCameraOnly = false,
                cameraLayout = CameraLayout.OFF,
                overlayVisible = false
            )
        }
    }

    // --- Feed Subsystems ---
    private fun startFeedSubsystems() {
        usbMonitorManager.start()
        audioCaptureEngine.start()
        mjpegPlayer.start()
        checkInitialUsbDevices()

        // Tap AudioStreamer into AudioCaptureEngine
        audioCaptureEngine.audioStreamer = audioStreamer

        // Connect 20 Hz meters from AudioCaptureEngine to ControlServer
        audioCaptureEngine.onMetersCalculated = { mic, cap ->
            controlServer?.updateMeters(mic, cap)
        }

        // Live audio status line for the Feed HUD
        audioCaptureEngine.onStatus = { status ->
            _uiState.update { it.copy(playerStats = it.playerStats.copy(audioStatus = status)) }
        }

        // Start Control Server
        startControlServer()
    }

    private fun stopFeedSubsystems() {
        stopControlServer()
        audioStreamer.stop()
        audioCaptureEngine.audioStreamer = null
        audioCaptureEngine.onMetersCalculated = null
        audioCaptureEngine.onStatus = null
        uvcCaptureEngine.close()
        activeCtrlBlock = null
        usbMonitorManager.stop()
        audioCaptureEngine.stop()
        mjpegPlayer.stop()
        try {
            androidx.camera.lifecycle.ProcessCameraProvider.getInstance(getApplication()).get().unbindAll()
        } catch (_: Throwable) {
        }
        _uiState.update {
            it.copy(
                isRemoteConnectedToFeed = false,
                isCameraOnly = false,
                overlayVisible = false,
                cameraLayout = CameraLayout.OFF,
                playerStats = PlayerStats()
            )
        }
    }

    private fun startControlServer() {
        if (controlServer != null) return

        val server = ControlServer(
            context = getApplication(),
            pairingPin = _uiState.value.feedPin,
            listener = object : ControlServerListener {
                override fun onRemoteConnected(remoteIp: String) {
                    _uiState.update { it.copy(isRemoteConnectedToFeed = true) }
                    try {
                        audioStreamer.setTargetAddress(InetAddress.getByName(remoteIp))
                    } catch (e: Throwable) {
                        Log.w(TAG, "Failed to resolve remote IP: ${e.message}")
                    }
                }

                override fun onRemoteDisconnected() {
                    _uiState.update { it.copy(isRemoteConnectedToFeed = false) }
                    audioStreamer.setStreamingEnabled(false)
                }

                override fun onSetGain(source: String, value: Float) {
                    if (source == Protocol.SRC_CAPTURE) {
                        feedSetCaptureGain(value)
                    } else if (source == Protocol.SRC_MIC) {
                        feedSetMicGain(value)
                    }
                }

                override fun onSetMute(source: String, muted: Boolean) {
                    if (source == Protocol.SRC_CAPTURE) {
                        feedSetCaptureMute(muted)
                    } else if (source == Protocol.SRC_MIC) {
                        feedSetMicMute(muted)
                    }
                }

                override fun onButtonClicked(actionId: String) {
                    buttonActionRegistry.execute(actionId)
                }

                override fun onSetLocalMonitor(enabled: Boolean) {
                    feedSetLocalMonitor(enabled)
                }

                override fun onAudioStartRequested(remoteIp: String) {
                    try {
                        audioStreamer.setTargetAddress(InetAddress.getByName(remoteIp))
                    } catch (_: Throwable) {
                    }
                    audioStreamer.setStreamingEnabled(true)
                    syncFeedStateToRemote()
                }

                override fun onAudioStopRequested() {
                    audioStreamer.setStreamingEnabled(false)
                    syncFeedStateToRemote()
                }
            }
        )

        controlServer = server
        server.start()
        audioStreamer.start(server.getPairingToken())

        _uiState.update {
            it.copy(
                feedServerIp = server.getLocalIpAddress(),
                feedState = Protocol.FeedState(
                    captureGain = audioCaptureEngine.getCaptureGain(),
                    captureMuted = audioCaptureEngine.isCaptureMuted(),
                    micGain = audioCaptureEngine.getMicGain(),
                    micMuted = audioCaptureEngine.isMicMuted(),
                    micAvailable = audioCaptureEngine.isMicAvailable(),
                    localMonitor = audioCaptureEngine.isLocalMonitorEnabled(),
                    audioStreaming = audioStreamer.isStreaming(),
                    buttonLabels = buttonActionRegistry.getLabels()
                )
            )
        }
    }

    private fun stopControlServer() {
        controlServer?.stop()
        controlServer = null
        _uiState.update { it.copy(isRemoteConnectedToFeed = false) }
    }

    private fun syncFeedStateToRemote() {
        val state = Protocol.FeedState(
            captureGain = audioCaptureEngine.getCaptureGain(),
            captureMuted = audioCaptureEngine.isCaptureMuted(),
            micGain = audioCaptureEngine.getMicGain(),
            micMuted = audioCaptureEngine.isMicMuted(),
            micAvailable = audioCaptureEngine.isMicAvailable(),
            localMonitor = audioCaptureEngine.isLocalMonitorEnabled(),
            audioStreaming = audioStreamer.isStreaming(),
            buttonLabels = buttonActionRegistry.getLabels()
        )
        _uiState.update { it.copy(feedState = state) }
        controlServer?.updateState(state)
    }

    fun feedSetCaptureGain(gain: Float) {
        audioCaptureEngine.setCaptureGain(gain)
        syncFeedStateToRemote()
    }

    fun feedSetCaptureMute(muted: Boolean) {
        audioCaptureEngine.setCaptureMuted(muted)
        syncFeedStateToRemote()
    }

    fun feedSetMicGain(gain: Float) {
        audioCaptureEngine.setMicGain(gain)
        syncFeedStateToRemote()
    }

    fun feedSetMicMute(muted: Boolean) {
        audioCaptureEngine.setMicMuted(muted)
        syncFeedStateToRemote()
    }

    fun feedToggleLocalMonitor() {
        val newLocal = !audioCaptureEngine.isLocalMonitorEnabled()
        audioCaptureEngine.setLocalMonitorEnabled(newLocal)
        syncFeedStateToRemote()
    }

    fun feedSetLocalMonitor(enabled: Boolean) {
        audioCaptureEngine.setLocalMonitorEnabled(enabled)
        syncFeedStateToRemote()
    }

    // --- Remote Subsystems ---
    private fun startRemoteSubsystems() {
        if (controlClient != null) return

        val client = ControlClient(
            context = getApplication(),
            listener = object : ControlClientListener {
                override fun onStateReceived(state: Protocol.FeedState) {
                    _uiState.update { it.copy(feedState = state) }
                }

                override fun onMetersReceived(mic: Protocol.SourceMeter, capture: Protocol.SourceMeter) {
                    _uiState.update {
                        it.copy(
                            remoteMicMeter = mic,
                            remoteCaptureMeter = capture
                        )
                    }
                }

                override fun onAuthFailed(reason: String) {
                    _uiState.update { it.copy(errorMessage = "PIN Authentication Failed: $reason") }
                }

                override fun onTokenAcquired(token: String) {
                    prefs.edit().putString(KEY_SAVED_TOKEN, token).apply()
                }
            }
        )

        controlClient = client
        client.startDiscovery()

        viewModelScope.launch {
            client.connectionStatus.collect { status ->
                _uiState.update { it.copy(remoteConnectionStatus = status) }
                if (status == ConnectionStatus.LOST) {
                    // Zero meters when connection drops
                    _uiState.update {
                        it.copy(
                            remoteMicMeter = Protocol.SourceMeter.SILENCE,
                            remoteCaptureMeter = Protocol.SourceMeter.SILENCE
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            client.discoveredFeeds.collect { feeds ->
                _uiState.update { it.copy(discoveredFeeds = feeds) }
            }
        }

        viewModelScope.launch {
            client.latencyMs.collect { rtt ->
                _uiState.update { it.copy(remoteLatencyMs = rtt) }
            }
        }

        // Auto-connect to last feed if saved
        val lastIp = prefs.getString(KEY_LAST_FEED_IP, null)
        val savedToken = prefs.getString(KEY_SAVED_TOKEN, null)
        if (!lastIp.isNullOrEmpty()) {
            remoteConnect(lastIp, "1234", savedToken)
        }
    }

    private fun stopRemoteSubsystems() {
        stopRemoteAudioStream()
        controlClient?.stop()
        controlClient = null
        _uiState.update {
            it.copy(
                isRemoteListening = false,
                remoteConnectionStatus = ConnectionStatus.SEARCHING,
                discoveredFeeds = emptyList(),
                remoteMicMeter = Protocol.SourceMeter.SILENCE,
                remoteCaptureMeter = Protocol.SourceMeter.SILENCE
            )
        }
    }

    fun remoteConnect(host: String, pin: String, token: String? = null) {
        prefs.edit().putString(KEY_LAST_FEED_IP, host).apply()
        _uiState.update { it.copy(connectedFeedIp = host) }
        val effectiveToken = token ?: prefs.getString(KEY_SAVED_TOKEN, null)
        controlClient?.connectTo(host, Protocol.CONTROL_PORT, pin, effectiveToken)
    }

    fun remoteReconnect() {
        controlClient?.reconnect()
    }

    fun remoteSetGain(source: String, value: Float) {
        controlClient?.setGain(source, value)
    }

    fun remoteSetMute(source: String, muted: Boolean) {
        controlClient?.setMute(source, muted)
    }

    fun remoteButtonClick(actionId: String) {
        controlClient?.sendButton(actionId)
    }

    fun remoteButtonClick(id: Int) {
        controlClient?.sendButton(id.toString())
    }

    fun remoteSetLocalMonitor(enabled: Boolean) {
        controlClient?.setLocalMonitor(enabled)
    }

    fun acceptEula() {
        com.example.ui.acceptEula(getApplication())
        _uiState.update { it.copy(currentScreen = CurrentScreen.START) }
    }

    fun remoteToggleListen() {
        val willListen = !_uiState.value.isRemoteListening
        _uiState.update { it.copy(isRemoteListening = willListen) }
        prefs.edit().putBoolean(KEY_REMOTE_LISTEN, willListen).apply()

        if (willListen) {
            controlClient?.startAudioStream()
            val host = _uiState.value.connectedFeedIp ?: "127.0.0.1"
            val token = prefs.getString(KEY_SAVED_TOKEN, "") ?: ""
            startRemoteAudioStream(host, token)
        } else {
            controlClient?.stopAudioStream()
            stopRemoteAudioStream()
        }
    }

    fun remoteSetMonitorVolume(volume: Float) {
        _uiState.update { it.copy(remoteMonitorVolume = volume) }
        prefs.edit().putFloat(KEY_REMOTE_VOL, volume).apply()
        remoteAudioPlayer?.setMonitorVolume(volume)
    }

    private fun startRemoteAudioStream(host: String, token: String) {
        RemoteAudioService.stopStreaming(getApplication())
        remoteAudioPlayer?.stop()
        remoteAudioPlayer = null
        val player = RemoteAudioPlayer(getApplication()) { stats ->
            _uiState.update { it.copy(remoteAudioStats = stats) }
        }
        remoteAudioPlayer = player
        player.setMonitorVolume(_uiState.value.remoteMonitorVolume)
        player.start(host, token)

        // Also start foreground service to keep playing when app is backgrounded
        RemoteAudioService.startStreaming(getApplication(), host, token)
    }

    private fun stopRemoteAudioStream() {
        remoteAudioPlayer?.stop()
        remoteAudioPlayer = null
        RemoteAudioService.stopStreaming(getApplication())
    }

    // --- USB and Capture Methods (Feed Mode) ---
    fun setSystemPermissions(hasCamera: Boolean, hasAudio: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = hasCamera,
                hasAudioPermission = hasAudio
            )
        }
        if (hasAudio && _uiState.value.currentScreen != CurrentScreen.REMOTE) {
            // Permission may have arrived after the audio pipeline already tried (and failed) to start
            audioCaptureEngine.ensureRunning()
        }
        if (hasCamera && _uiState.value.currentScreen != CurrentScreen.REMOTE) {
            checkInitialUsbDevices()
        }
    }

    private fun checkInitialUsbDevices() {
        if (_uiState.value.currentScreen == CurrentScreen.REMOTE) return
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
                hasUsbPermission = true,
                currentScreen = CurrentScreen.PLAYER
            )
        }

        val modes = uvcCaptureEngine.open(ctrlBlock)
        _uiState.update { it.copy(supportedQualityModes = modes) }

        val savedQualityId = prefs.getString(KEY_QUALITY_MODE, QualityMode.ID_720P_60)
        val selected = modes.find { it.id == savedQualityId && it.isSupportedByCard }
            ?: modes.find { it.id == QualityMode.ID_720P_60 && it.isSupportedByCard }
            ?: modes.find { it.isSupportedByCard }
            ?: modes.firstOrNull()
            ?: QualityMode.DEFAULT_720P_60

        _uiState.update {
            it.copy(currentQualityMode = selected)
        }

        uvcCaptureEngine.startStream(selected)
    }

    private fun onUsbDeviceClosed(device: UsbDevice) {
        if (_uiState.value.attachedDevice?.deviceId == device.deviceId) {
            uvcCaptureEngine.close()
            activeCtrlBlock = null
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

    // --- PNG overlay (Feed Player) ---
    fun setOverlayImage(uri: String) {
        prefs.edit().putString("pref_overlay_uri", uri).putBoolean("pref_overlay_visible", true).apply()
        _uiState.update { it.copy(overlayUri = uri, overlayVisible = true) }
    }

    fun toggleOverlay() {
        val visible = !_uiState.value.overlayVisible
        prefs.edit().putBoolean("pref_overlay_visible", visible).apply()
        _uiState.update { it.copy(overlayVisible = visible) }
    }

    fun saveOverlayTransform(cx: Float, cy: Float, width: Float) {
        prefs.edit()
            .putFloat("pref_overlay_cx", cx)
            .putFloat("pref_overlay_cy", cy)
            .putFloat("pref_overlay_w", width)
            .apply()
        _uiState.update { it.copy(overlayCx = cx, overlayCy = cy, overlayWidth = width) }
    }

    fun dismissFeedbackWarning() {
        _uiState.update { it.copy(feedbackWarningVisible = false) }
    }

    fun navigateToConnect() {
        _uiState.update { it.copy(currentScreen = CurrentScreen.CONNECT) }
    }

    fun setCameraLayout(layout: CameraLayout) {
        _uiState.update { it.copy(cameraLayout = layout) }
        prefs.edit().putInt(KEY_CAMERA_LAYOUT, layout.ordinal).apply()
    }

    fun cycleCameraLayout() {
        val next = _uiState.value.cameraLayout.next()
        setCameraLayout(next)
    }

    fun flipCamera() {
        val newFront = !_uiState.value.cameraFront
        _uiState.update { it.copy(cameraFront = newFront) }
        prefs.edit().putBoolean(KEY_CAMERA_FRONT, newFront).apply()
    }

    fun cycleCameraPipSize() {
        val next = _uiState.value.cameraPipSize.next()
        setCameraPipSize(next)
    }

    fun setCameraPipSize(size: CameraPipSize) {
        _uiState.update { it.copy(cameraPipSize = size) }
        prefs.edit().putInt(KEY_CAMERA_PIP_SIZE, size.ordinal).apply()
    }

    fun cycleCameraAspectRatio() {
        val next = _uiState.value.cameraAspectRatio.next()
        setCameraAspectRatio(next)
    }

    fun setCameraAspectRatio(ratio: CameraAspectRatio) {
        _uiState.update { it.copy(cameraAspectRatio = ratio) }
        prefs.edit().putInt(KEY_CAMERA_ASPECT_RATIO, ratio.ordinal).apply()
    }

    fun toggleCameraBorder() {
        setCameraBorder(!_uiState.value.cameraBorder)
    }

    fun setCameraBorder(enabled: Boolean) {
        _uiState.update { it.copy(cameraBorder = enabled) }
        prefs.edit().putBoolean(KEY_CAMERA_BORDER, enabled).apply()
    }

    override fun onCleared() {
        super.onCleared()
        stopFeedSubsystems()
        stopRemoteSubsystems()
        usbMonitorManager.destroy()
    }
}
