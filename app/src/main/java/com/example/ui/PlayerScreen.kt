package com.example.ui

import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Crop
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.audio.AudioInputDevice
import com.example.capture.QualityMode
import com.example.capture.UvcCaptureEngine
import com.example.player.MjpegPlayer
import com.example.player.PlayerStats
import com.example.player.ScaleMode
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    uvcCaptureEngine: UvcCaptureEngine,
    mjpegPlayer: MjpegPlayer,
    stats: PlayerStats,
    currentQualityMode: QualityMode,
    supportedQualityModes: List<QualityMode>,
    scaleMode: ScaleMode,
    isStatsOverlayVisible: Boolean,
    availableAudioDevices: List<AudioInputDevice>,
    selectedAudioDevice: AudioInputDevice?,
    audioVolume: Float,
    isAudioMuted: Boolean,
    localMonitorEnabled: Boolean,
    feedServerIp: String,
    feedPin: String,
    isRemoteConnected: Boolean,
    feedbackWarningVisible: Boolean,
    onSelectQualityMode: (QualityMode) -> Unit,
    onToggleScaleMode: () -> Unit,
    onToggleStatsOverlay: () -> Unit,
    onSelectAudioDevice: (AudioInputDevice) -> Unit,
    onAudioVolumeChange: (Float) -> Unit,
    onToggleAudioMute: () -> Unit,
    onToggleLocalMonitor: () -> Unit,
    onDismissFeedbackWarning: () -> Unit,
    onNavigateBack: () -> Unit,
    overlayUri: String? = null,
    overlayVisible: Boolean = false,
    overlayCx: Float = 0.5f,
    overlayCy: Float = 0.5f,
    overlayWidth: Float = 0.3f,
    onOverlayTransformChanged: (Float, Float, Float) -> Unit = { _, _, _ -> },
    onPickOverlay: () -> Unit = {},
    onToggleOverlay: () -> Unit = {},
    onResetOverlayPosition: () -> Unit = {},
    onClearOverlay: () -> Unit = {},
    cameraLayout: CameraLayout = CameraLayout.OFF,
    cameraFront: Boolean = false,
    cameraPipSize: CameraPipSize = CameraPipSize.LARGE,
    cameraAspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    cameraBorder: Boolean = true,
    onCycleCameraLayout: () -> Unit = {},
    onSelectCameraLayout: (CameraLayout) -> Unit = {},
    onFlipCamera: () -> Unit = {},
    onCycleCameraPipSize: () -> Unit = {},
    onSelectCameraPipSize: (CameraPipSize) -> Unit = {},
    onCycleCameraAspectRatio: () -> Unit = {},
    onSelectCameraAspectRatio: (CameraAspectRatio) -> Unit = {},
    onToggleCameraBorder: () -> Unit = {},
    isCameraOnly: Boolean = false,
    attachedDeviceName: String? = null,
    onSwitchToCaptureMode: () -> Unit = {},
    onNavigateToStart: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val studioCameraState = rememberStudioCameraState(
        hasUsbCapture = attachedDeviceName != null,
        mainFront = cameraFront,
        cameraLayout = cameraLayout,
        hasCameraPermission = hasCameraPermission
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && attachedDeviceName == null) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        onNavigateBack()
    }

    // Controls visibility with 4-second auto-hide
    var controlsVisible by remember { mutableStateOf(true) }
    var userTouchTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    var showQualitySheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showCameraSheet by remember { mutableStateOf(false) }
    var showOverlaySheet by remember { mutableStateOf(false) }

    // Keep screen on and apply immersive fullscreen mode
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.let { win ->
            val controller = WindowCompat.getInsetsController(win, win.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { win ->
                val controller = WindowCompat.getInsetsController(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(controlsVisible, userTouchTimestamp) {
        if (controlsVisible && !showQualitySheet && !showAudioSheet && !showCameraSheet && !showOverlaySheet) {
            delay(4000L)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                controlsVisible = !controlsVisible
                userTouchTimestamp = System.currentTimeMillis()
            }
    ) {
        // 1. Primary Full-Screen View:
        // - If USB capture card is connected: SurfaceView for MjpegPlayer
        // - If NO USB capture card: Main Camera is rendered FULL SCREEN
        if (attachedDeviceName != null) {
            val surfaceModifier = when (cameraLayout) {
                CameraLayout.SPLIT_CAMERA_TOP -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                CameraLayout.SPLIT_CAMERA_BOTTOM -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.TopCenter)
                else -> Modifier.fillMaxSize()
            }

            if (cameraLayout != CameraLayout.FULL_SCREEN) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    mjpegPlayer.onSurfaceCreated(holder)
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder,
                                    format: Int,
                                    width: Int,
                                    height: Int
                                ) {
                                    mjpegPlayer.onSurfaceChanged(holder, width, height)
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    mjpegPlayer.onSurfaceDestroyed()
                                }
                            })
                        }
                    },
                    modifier = surfaceModifier.testTag("surface_player_view")
                )
            }
        } else {
            // Main camera is full screen when no USB device is connected
            val mainCameraModifier = when (cameraLayout) {
                CameraLayout.SPLIT_CAMERA_TOP -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                CameraLayout.SPLIT_CAMERA_BOTTOM -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.TopCenter)
                else -> Modifier.fillMaxSize()
            }

            if (!hasCameraPermission || studioCameraState.permissionDenied) {
                Box(
                    modifier = mainCameraModifier
                        .background(Color(0xFF0A0F1D))
                        .testTag("camera_permission_required_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF161B2C)),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF2563EB)),
                        modifier = Modifier
                            .padding(24.dp)
                            .widthIn(max = 420.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E3A8A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color(0xFF93C5FD),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Enable camera permission to stream with your phone's camera lenses and overlays.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("grant_camera_permission_button")
                            ) {
                                Text(
                                    text = "Grant Camera Permission",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else if (studioCameraState.noCameraAvailable) {
                Box(
                    modifier = mainCameraModifier
                        .background(Color(0xFF0A0F1D)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Camera Hardware Found",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Connect a USB capture card or test on a physical Android device with cameras.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        (studioCameraState.primaryPreviewView.parent as? android.view.ViewGroup)?.removeView(studioCameraState.primaryPreviewView)
                        studioCameraState.primaryPreviewView
                    },
                    modifier = mainCameraModifier.testTag("main_camera_preview")
                )
            }
        }

        // 2. Secondary PiP View:
        // - In USB mode: PiP displays the selected camera (cameraFront)
        // - In Camera-only (no USB) mode: If user opts for PiP, PiP displays the OPPOSITE camera (!cameraFront)!
        val showPip = if (attachedDeviceName != null) {
            cameraLayout != CameraLayout.OFF
        } else {
            cameraLayout != CameraLayout.OFF && cameraLayout != CameraLayout.FULL_SCREEN
        }

        if (showPip) {
            val cornerShape = RoundedCornerShape(12.dp)
            val pipModifier = when (cameraLayout) {
                CameraLayout.SPLIT_CAMERA_TOP -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.TopCenter)
                    .clipToBounds()
                CameraLayout.SPLIT_CAMERA_BOTTOM -> Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
                CameraLayout.PIP_TOP_LEFT -> Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .fillMaxWidth(cameraPipSize.widthFraction)
                    .aspectRatio(cameraAspectRatio.ratio)
                    .clip(cornerShape)
                    .then(if (cameraBorder) Modifier.border(1.dp, Color.White, cornerShape) else Modifier)
                CameraLayout.PIP_TOP_RIGHT -> Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .fillMaxWidth(cameraPipSize.widthFraction)
                    .aspectRatio(cameraAspectRatio.ratio)
                    .clip(cornerShape)
                    .then(if (cameraBorder) Modifier.border(1.dp, Color.White, cornerShape) else Modifier)
                CameraLayout.PIP_BOTTOM_LEFT -> Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .fillMaxWidth(cameraPipSize.widthFraction)
                    .aspectRatio(cameraAspectRatio.ratio)
                    .clip(cornerShape)
                    .then(if (cameraBorder) Modifier.border(1.dp, Color.White, cornerShape) else Modifier)
                CameraLayout.PIP_BOTTOM_RIGHT -> Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .fillMaxWidth(cameraPipSize.widthFraction)
                    .aspectRatio(cameraAspectRatio.ratio)
                    .clip(cornerShape)
                    .then(if (cameraBorder) Modifier.border(1.dp, Color.White, cornerShape) else Modifier)
                else -> Modifier
            }

            Box(
                modifier = pipModifier
                    .background(Color(0xFF161B2C), cornerShape)
                    .clipToBounds()
            ) {
                if (attachedDeviceName != null) {
                    AndroidView(
                        factory = { ctx ->
                            (studioCameraState.secondaryPreviewView.parent as? android.view.ViewGroup)?.removeView(studioCameraState.secondaryPreviewView)
                            studioCameraState.secondaryPreviewView
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("camera_pip_preview")
                    )
                } else {
                    if (studioCameraState.isConcurrentSupported) {
                        AndroidView(
                            factory = { ctx ->
                                (studioCameraState.secondaryPreviewView.parent as? android.view.ViewGroup)?.removeView(studioCameraState.secondaryPreviewView)
                                studioCameraState.secondaryPreviewView
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("camera_pip_preview")
                        )
                    } else {
                        DualCameraUnavailablePip(
                            oppositeCameraFront = !cameraFront,
                            onFlipCamera = onFlipCamera,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // User PNG overlay: drag with one finger, pinch to resize (button 6 on the Remote toggles it)
        PngOverlay(
            uri = overlayUri,
            visible = overlayVisible,
            centerX = overlayCx,
            centerY = overlayCy,
            widthFraction = overlayWidth,
            onTransformChanged = onOverlayTransformChanged
        )

        // Real-time HUD Performance Stats Overlay
        StatsOverlay(
            stats = stats,
            resolution = if (attachedDeviceName != null) {
                "${currentQualityMode.targetWidth}x${currentQualityMode.targetHeight}"
            } else {
                if (cameraLayout != CameraLayout.OFF && studioCameraState.isConcurrentSupported) {
                    "DUAL CAM: ${if (cameraFront) "FRONT+BACK" else "BACK+FRONT"}"
                } else {
                    "CAMERA: ${if (cameraFront) "FRONT" else "BACK"}"
                }
            },
            isVisible = isStatsOverlayVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
        )

        // Top Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Switch role / Home button
                        IconButton(
                            onClick = onNavigateToStart,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("player_home_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Switch role",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Current Mode Pill
                        Surface(
                            color = Color(0xFF1E293B).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (attachedDeviceName != null) Color(0xFF3B82F6) else Color(0xFF10B981)
                            ),
                            modifier = Modifier
                                .clickable {
                                    if (attachedDeviceName != null) {
                                        showQualitySheet = true
                                        userTouchTimestamp = System.currentTimeMillis()
                                    } else {
                                        onFlipCamera()
                                        userTouchTimestamp = System.currentTimeMillis()
                                    }
                                }
                                .testTag("player_mode_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (attachedDeviceName != null) Icons.Default.Videocam else Icons.Default.Cameraswitch,
                                    contentDescription = if (attachedDeviceName != null) "Quality mode" else "Flip Camera Lens",
                                    tint = if (attachedDeviceName != null) Color(0xFF60A5FA) else Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (attachedDeviceName != null) currentQualityMode.label else "CAMERA: ${if (cameraFront) "FRONT" else "BACK"}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Wireless Feed Server IP & PIN Pill
                        Surface(
                            color = Color(0xFF161B28).copy(alpha = 0.9f),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isRemoteConnected) Color(0xFF10B981) else Color(0xFF475569)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (isRemoteConnected) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$feedServerIp  PIN:$feedPin",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Top Action Icons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Fit/Fill quick button
                        IconButton(
                            onClick = {
                                onToggleScaleMode()
                                userTouchTimestamp = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("toggle_scale_mode_button")
                        ) {
                            Icon(
                                imageVector = if (scaleMode == ScaleMode.FIT) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                                contentDescription = if (scaleMode == ScaleMode.FIT) "Fit Letterbox" else "Fill Screen",
                                tint = if (scaleMode == ScaleMode.FILL) Color(0xFF60A5FA) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Camera layout cycling button
                        IconButton(
                            onClick = {
                                onCycleCameraLayout()
                                userTouchTimestamp = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .background(
                                    if (cameraLayout != CameraLayout.OFF) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .testTag("toggle_camera_layout_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Camera layout: ${cameraLayout.label}",
                                tint = if (cameraLayout != CameraLayout.OFF) Color(0xFF93C5FD) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Quick buttons for Size, Aspect Ratio, and Border when PiP is active
                        if (cameraLayout != CameraLayout.OFF && !cameraLayout.isSplit && cameraLayout != CameraLayout.FULL_SCREEN) {
                            IconButton(
                                onClick = {
                                    onCycleCameraPipSize()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("toggle_camera_pip_size_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoSizeSelectLarge,
                                    contentDescription = "PiP size: ${cameraPipSize.label}",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    onCycleCameraAspectRatio()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("toggle_camera_aspect_ratio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Crop,
                                    contentDescription = "Aspect ratio: ${cameraAspectRatio.label}",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    onToggleCameraBorder()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .background(
                                        if (cameraBorder) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                                    .testTag("toggle_camera_border_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BorderStyle,
                                    contentDescription = if (cameraBorder) "Border: ON" else "Border: OFF",
                                    tint = if (cameraBorder) Color(0xFF93C5FD) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Camera settings sheet button
                        if (cameraLayout != CameraLayout.OFF) {
                            IconButton(
                                onClick = {
                                    showCameraSheet = true
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("open_camera_settings_sheet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Camera settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Flip camera button (shown when layout is not OFF)
                        if (cameraLayout != CameraLayout.OFF) {
                            IconButton(
                                onClick = {
                                    onFlipCamera()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .testTag("flip_camera_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = if (cameraFront) "Front camera" else "Back camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Choose the PNG overlay image
                        IconButton(
                            onClick = {
                                showOverlaySheet = true
                                userTouchTimestamp = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .background(
                                    if (overlayVisible && overlayUri != null) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .testTag("pick_overlay_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Stream Overlay & Watermark",
                                tint = if (overlayVisible && overlayUri != null) Color(0xFF93C5FD) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Performance HUD dynamic toggle
                        IconButton(
                            onClick = {
                                onToggleStatsOverlay()
                                userTouchTimestamp = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .background(
                                    if (isStatsOverlayVisible) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .testTag("toggle_stats_hud_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = "Performance HUD",
                                tint = if (isStatsOverlayVisible) Color(0xFF93C5FD) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Audio mute quick toggle
                        IconButton(
                            onClick = {
                                onToggleAudioMute()
                                userTouchTimestamp = System.currentTimeMillis()
                            },
                            modifier = Modifier
                                .background(
                                    if (isAudioMuted) Color(0xFF7F1D1D).copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.5f),
                                    CircleShape
                                )
                                .testTag("player_mute_toggle")
                        ) {
                            Icon(
                                imageVector = if (isAudioMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = if (isAudioMuted) "Unmute Audio" else "Mute Audio",
                                tint = if (isAudioMuted) Color(0xFFFCA5A5) else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFF1E2232).copy(alpha = 0.95f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Quality / Camera Lens button
                            ControlBarPill(
                                icon = if (attachedDeviceName != null) Icons.Default.Videocam else Icons.Default.Cameraswitch,
                                label = if (attachedDeviceName != null) currentQualityMode.id.uppercase() else if (cameraFront) "FRONT" else "BACK",
                                onClick = {
                                    if (attachedDeviceName != null) {
                                        showQualitySheet = true
                                        userTouchTimestamp = System.currentTimeMillis()
                                    } else {
                                        onFlipCamera()
                                        userTouchTimestamp = System.currentTimeMillis()
                                    }
                                },
                                tag = "open_quality_sheet_button"
                            )

                            // Audio button
                            ControlBarPill(
                                icon = Icons.Default.Headphones,
                                label = if (isAudioMuted) "MUTED" else "${(audioVolume * 100).toInt()}%",
                                isWarning = isAudioMuted,
                                onClick = {
                                    showAudioSheet = true
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                tag = "open_audio_sheet_button"
                            )

                            // Fit / Fill toggle button
                            ControlBarPill(
                                icon = if (scaleMode == ScaleMode.FIT) Icons.Default.AspectRatio else Icons.Default.FitScreen,
                                label = if (scaleMode == ScaleMode.FIT) "FIT" else "FILL",
                                isActive = scaleMode == ScaleMode.FILL,
                                onClick = {
                                    onToggleScaleMode()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                tag = "bar_fit_fill_toggle"
                            )

                            // Camera settings pill
                            ControlBarPill(
                                icon = Icons.Default.CameraAlt,
                                label = if (attachedDeviceName != null) {
                                    when (cameraLayout) {
                                        CameraLayout.OFF -> "CAM OFF"
                                        CameraLayout.FULL_SCREEN -> "CAM FULL"
                                        else -> "CAM ${cameraPipSize.label.uppercase()} ${cameraAspectRatio.label}"
                                    }
                                } else {
                                    when (cameraLayout) {
                                        CameraLayout.OFF -> "PIP OFF"
                                        CameraLayout.FULL_SCREEN -> "PIP FULL"
                                        else -> "PIP (${if (!cameraFront) "FRONT" else "BACK"})"
                                    }
                                },
                                isActive = cameraLayout != CameraLayout.OFF,
                                onClick = {
                                    showCameraSheet = true
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                tag = "bar_camera_settings_toggle"
                            )

                            // Overlay pill
                            ControlBarPill(
                                icon = Icons.Default.Layers,
                                label = when {
                                    overlayUri == null -> "+ OVERLAY"
                                    overlayVisible -> "OVERLAY ON"
                                    else -> "OVERLAY OFF"
                                },
                                isActive = overlayVisible && overlayUri != null,
                                onClick = {
                                    showOverlaySheet = true
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                tag = "bar_overlay_settings_toggle"
                            )

                            // Stats HUD toggle button
                            ControlBarPill(
                                icon = Icons.Default.BarChart,
                                label = if (isStatsOverlayVisible) "HUD ON" else "HUD OFF",
                                isActive = isStatsOverlayVisible,
                                onClick = {
                                    onToggleStatsOverlay()
                                    userTouchTimestamp = System.currentTimeMillis()
                                },
                                tag = "bar_stats_toggle"
                            )
                        }
                    }
                }
            }
        }

        // Acoustic Feedback / Howl-round Alert Dialog
        if (feedbackWarningVisible) {
            AlertDialog(
                onDismissRequest = onDismissFeedbackWarning,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(32.dp)
                    )
                },
                title = {
                    Text("Acoustic Feedback Detected")
                },
                text = {
                    Text("Anti-feedback protection was triggered. Audio output has been muted automatically to prevent speaker howling.")
                },
                confirmButton = {
                    Button(
                        onClick = onDismissFeedbackWarning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Understood")
                    }
                },
                containerColor = Color(0xFF1E2232),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCBD5E1)
            )
        }

        // Quality Sheet
        if (showQualitySheet) {
            QualitySelectorSheet(
                modes = supportedQualityModes,
                currentMode = currentQualityMode,
                stats = stats,
                onSelectMode = { newMode ->
                    onSelectQualityMode(newMode)
                },
                onDismiss = {
                    showQualitySheet = false
                    userTouchTimestamp = System.currentTimeMillis()
                }
            )
        }

        // Audio Sheet with Local Monitor toggle
        if (showAudioSheet) {
            AudioControlsSheet(
                devices = availableAudioDevices,
                selectedDevice = selectedAudioDevice,
                volume = audioVolume,
                isMuted = isAudioMuted,
                localMonitorEnabled = localMonitorEnabled,
                onSelectDevice = onSelectAudioDevice,
                onVolumeChange = onAudioVolumeChange,
                onToggleMute = onToggleAudioMute,
                onToggleLocalMonitor = onToggleLocalMonitor,
                onDismiss = {
                    showAudioSheet = false
                    userTouchTimestamp = System.currentTimeMillis()
                }
            )
        }

        // Camera Settings Sheet
        if (showCameraSheet) {
            CameraSettingsSheet(
                cameraLayout = cameraLayout,
                cameraPipSize = cameraPipSize,
                cameraAspectRatio = cameraAspectRatio,
                cameraBorder = cameraBorder,
                cameraFront = cameraFront,
                hasUsbCapture = attachedDeviceName != null,
                isCameraOnly = isCameraOnly,
                onSelectLayout = onSelectCameraLayout,
                onSelectSize = onSelectCameraPipSize,
                onSelectAspectRatio = onSelectCameraAspectRatio,
                onToggleBorder = { onToggleCameraBorder() },
                onFlipCamera = onFlipCamera,
                onDismiss = {
                    showCameraSheet = false
                    userTouchTimestamp = System.currentTimeMillis()
                }
            )
        }

        // Overlay Settings Sheet
        if (showOverlaySheet) {
            OverlaySettingsSheet(
                overlayUri = overlayUri,
                overlayVisible = overlayVisible,
                onPickOverlay = onPickOverlay,
                onToggleOverlay = onToggleOverlay,
                onResetPosition = onResetOverlayPosition,
                onClearOverlay = onClearOverlay,
                onDismiss = {
                    showOverlaySheet = false
                    userTouchTimestamp = System.currentTimeMillis()
                }
            )
        }
    }
}

@Composable
private fun ControlBarPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean = false,
    isWarning: Boolean = false,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        color = when {
            isWarning -> Color(0xFF7F1D1D).copy(alpha = 0.8f)
            isActive -> Color(0xFF2563EB).copy(alpha = 0.8f)
            else -> Color(0xFF0F172A).copy(alpha = 0.7f)
        },
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) Color(0xFF60A5FA) else Color(0xFF334155)
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    isWarning -> Color(0xFFFCA5A5)
                    isActive -> Color(0xFF93C5FD)
                    else -> Color(0xFFCBD5E1)
                },
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 11.sp
                )
            )
        }
    }
}
