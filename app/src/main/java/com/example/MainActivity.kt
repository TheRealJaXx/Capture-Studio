package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ConnectScreen
import com.example.ui.CurrentScreen
import com.example.ui.MainViewModel
import com.example.ui.PlayerScreen
import com.example.ui.RemoteScreen
import com.example.ui.StartScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true

        viewModel.setSystemPermissions(cameraGranted, audioGranted)

        if (!cameraGranted) {
            Toast.makeText(
                this,
                "Camera permission is required for USB capture card video.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val pickOverlayLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Throwable) {
            }
            viewModel.setOverlayImage(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        enableEdgeToEdge()
        CrashReporter.showIfAny(this)

        checkAndRequestSystemPermissions()

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    LaunchedEffect(uiState.errorMessage) {
                        uiState.errorMessage?.let { msg ->
                            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }

                    when (uiState.currentScreen) {
                        CurrentScreen.START -> {
                            StartScreen(
                                onSelectFeed = {
                                    viewModel.selectFeedMode()
                                },
                                onSelectRemote = {
                                    viewModel.selectRemoteMode()
                                }
                            )
                        }

                        CurrentScreen.CONNECT -> {
                            ConnectScreen(
                                attachedDevice = uiState.attachedDevice,
                                hasUsbPermission = uiState.hasUsbPermission,
                                hasCameraPermission = uiState.hasCameraPermission,
                                hasAudioPermission = uiState.hasAudioPermission,
                                onRequestUsbPermission = {
                                    viewModel.requestUsbPermission()
                                },
                                onRequestSystemPermissions = {
                                    checkAndRequestSystemPermissions()
                                },
                                onStartPlayer = {
                                    viewModel.startPlayerManually()
                                }
                            )
                        }

                        CurrentScreen.PLAYER -> {
                            PlayerScreen(
                                uvcCaptureEngine = viewModel.uvcCaptureEngine,
                                mjpegPlayer = viewModel.mjpegPlayer,
                                stats = uiState.playerStats,
                                currentQualityMode = uiState.currentQualityMode,
                                supportedQualityModes = uiState.supportedQualityModes,
                                scaleMode = uiState.scaleMode,
                                isStatsOverlayVisible = uiState.isStatsOverlayVisible,
                                availableAudioDevices = uiState.availableAudioDevices,
                                selectedAudioDevice = uiState.selectedAudioDevice,
                                audioVolume = uiState.audioVolume,
                                isAudioMuted = uiState.isAudioMuted,
                                localMonitorEnabled = uiState.feedState.localMonitor,
                                feedServerIp = uiState.feedServerIp,
                                feedPin = uiState.feedPin,
                                isRemoteConnected = uiState.isRemoteConnectedToFeed,
                                feedbackWarningVisible = uiState.feedbackWarningVisible,
                                onSelectQualityMode = { mode ->
                                    viewModel.selectQualityMode(mode)
                                },
                                onToggleScaleMode = {
                                    viewModel.toggleScaleMode()
                                },
                                onToggleStatsOverlay = {
                                    viewModel.toggleStatsOverlay()
                                },
                                onSelectAudioDevice = { dev ->
                                    viewModel.selectAudioDevice(dev)
                                },
                                onAudioVolumeChange = { vol ->
                                    viewModel.setAudioVolume(vol)
                                },
                                onToggleAudioMute = {
                                    viewModel.toggleAudioMute()
                                },
                                onToggleLocalMonitor = {
                                    viewModel.feedToggleLocalMonitor()
                                },
                                onDismissFeedbackWarning = {
                                    viewModel.dismissFeedbackWarning()
                                },
                                onNavigateBack = {
                                    viewModel.navigateToConnect()
                                },
                                overlayUri = uiState.overlayUri,
                                overlayVisible = uiState.overlayVisible,
                                overlayCx = uiState.overlayCx,
                                overlayCy = uiState.overlayCy,
                                overlayWidth = uiState.overlayWidth,
                                onOverlayTransformChanged = { cx, cy, w ->
                                    viewModel.saveOverlayTransform(cx, cy, w)
                                },
                                onPickOverlay = {
                                    pickOverlayLauncher.launch(arrayOf("image/*"))
                                }
                            )
                        }

                        CurrentScreen.REMOTE -> {
                            RemoteScreen(
                                connectionStatus = uiState.remoteConnectionStatus,
                                discoveredFeeds = uiState.discoveredFeeds,
                                connectedFeedIp = uiState.connectedFeedIp,
                                latencyMs = uiState.remoteLatencyMs,
                                feedState = uiState.feedState,
                                micMeter = uiState.remoteMicMeter,
                                captureMeter = uiState.remoteCaptureMeter,
                                audioStats = uiState.remoteAudioStats,
                                isListening = uiState.isRemoteListening,
                                monitorVolume = uiState.remoteMonitorVolume,
                                onSetGain = { src, v ->
                                    viewModel.remoteSetGain(src, v)
                                },
                                onSetMute = { src, muted ->
                                    viewModel.remoteSetMute(src, muted)
                                },
                                onButtonClick = { id ->
                                    viewModel.remoteButtonClick(id)
                                },
                                onToggleListen = {
                                    viewModel.remoteToggleListen()
                                },
                                onSetMonitorVolume = { vol ->
                                    viewModel.remoteSetMonitorVolume(vol)
                                },
                                onConnectToFeed = { ip, pin ->
                                    viewModel.remoteConnect(ip, pin)
                                },
                                onReconnect = {
                                    viewModel.remoteReconnect()
                                },
                                onNavigateBack = {
                                    viewModel.navigateToStart()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestSystemPermissions() {
        val cameraGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.setSystemPermissions(cameraGranted, audioGranted)

        val permissionsToRequest = mutableListOf<String>()
        if (!cameraGranted) permissionsToRequest.add(Manifest.permission.CAMERA)
        if (!audioGranted) permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
