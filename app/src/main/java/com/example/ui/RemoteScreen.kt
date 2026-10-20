package com.example.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.net.ConnectionStatus
import com.example.net.DiscoveredFeed
import com.example.net.Protocol
import com.example.net.RemoteAudioStats
import kotlin.math.log10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    connectionStatus: ConnectionStatus,
    discoveredFeeds: List<DiscoveredFeed>,
    connectedFeedIp: String?,
    latencyMs: Long,
    feedState: Protocol.FeedState,
    micMeter: Protocol.SourceMeter,
    captureMeter: Protocol.SourceMeter,
    audioStats: RemoteAudioStats,
    isListening: Boolean,
    monitorVolume: Float,
    onSetGain: (String, Float) -> Unit,
    onSetMute: (String, Boolean) -> Unit,
    onButtonClick: (String) -> Unit,
    onSetLocalMonitor: (Boolean) -> Unit = {},
    onToggleListen: () -> Unit,
    onSetMonitorVolume: (Float) -> Unit,
    onConnectToFeed: (String, String) -> Unit,
    onReconnect: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    val requestLeave = {
        if (connectionStatus == ConnectionStatus.CONNECTED) {
            showDisconnectConfirmDialog = true
        } else {
            onNavigateBack()
        }
    }

    BackHandler {
        requestLeave()
    }

    // Keep screen on in Remote mode
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var showConnectionSheet by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf(connectedFeedIp ?: "192.168.1.") }
    var pinInput by remember { mutableStateOf("1234") }

    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    val enabledPages = remember { com.example.net.ShortcutConfig.getEnabledPages() }
    val totalPages = enabledPages.size

    // State for toggled/selected buttons on Remote
    var activeShortcuts by remember {
        mutableStateOf(setOf<String>())
    }

    // Current page of shortcuts (0-indexed across enabled pages)
    var currentShortcutPage by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // 1. Slim Top Bar: [Connected] on left, STREAM CAPTURE in center, Setup on right
            RemoteTopBar(
                connectionStatus = connectionStatus,
                latencyMs = latencyMs,
                onBack = requestLeave,
                onOpenSetup = { showConnectionSheet = true },
                onReconnect = onReconnect
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Dual Channel Audio Section (MIC/AUX and GAME AUDIO) matching reference image
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // MIC / AUX Channel Card (Cyan theme)
                AudioChannelStrip(
                    title = "MIC/AUX",
                    isCapture = false,
                    isAvailable = feedState.micAvailable && isConnected,
                    gain = feedState.micGain,
                    isMuted = feedState.micMuted,
                    meter = if (isConnected) micMeter else Protocol.SourceMeter.SILENCE,
                    isListening = false,
                    monitorVolume = 1.0f,
                    audioStats = null,
                    onGainChange = { onSetGain(Protocol.SRC_MIC, it) },
                    onToggleMute = { onSetMute(Protocol.SRC_MIC, !feedState.micMuted) },
                    onToggleListen = {},
                    onMonitorVolumeChange = {},
                    modifier = Modifier.weight(1f)
                )

                // GAME AUDIO Channel Card (Amber theme)
                AudioChannelStrip(
                    title = "GAME AUDIO",
                    isCapture = true,
                    isAvailable = isConnected,
                    gain = feedState.captureGain,
                    isMuted = feedState.captureMuted,
                    meter = if (isConnected) captureMeter else Protocol.SourceMeter.SILENCE,
                    isListening = isListening,
                    monitorVolume = monitorVolume,
                    audioStats = if (isListening) audioStats else null,
                    onGainChange = { onSetGain(Protocol.SRC_CAPTURE, it) },
                    onToggleMute = { onSetMute(Protocol.SRC_CAPTURE, !feedState.captureMuted) },
                    onToggleListen = onToggleListen,
                    onMonitorVolumeChange = onSetMonitorVolume,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Shortcuts Section (2x3 Grid with Activators and Left/Right Paging)
            ShortcutGridSection(
                page = currentShortcutPage,
                pages = enabledPages,
                activeShortcuts = activeShortcuts,
                micMuted = feedState.micMuted,
                localMonitor = feedState.localMonitor,
                isEnabled = isConnected,
                onPrevPage = {
                    if (currentShortcutPage > 0) currentShortcutPage--
                },
                onNextPage = {
                    if (currentShortcutPage < totalPages - 1) currentShortcutPage++
                },
                onButtonClick = { id ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    activeShortcuts = if (activeShortcuts.contains(id)) {
                        activeShortcuts - id
                    } else {
                        activeShortcuts + id
                    }
                    onButtonClick(id)
                },
                onSetLocalMonitor = { enabled ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetLocalMonitor(enabled)
                },
                onToggleMicMute = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetMute(Protocol.SRC_MIC, !feedState.micMuted)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Sponsor / Studio Status Bottom Capsule (Matching reference image)
            SponsorBottomCapsule(
                onOpenSetup = { showConnectionSheet = true }
            )
        }

        // Connection / Feed Selection Bottom Sheet
        if (showConnectionSheet) {
            ConnectionBottomSheet(
                discoveredFeeds = discoveredFeeds,
                manualIp = manualIpInput,
                pin = pinInput,
                onManualIpChange = { manualIpInput = it },
                onPinChange = { pinInput = it },
                onSelectDiscovered = { feed ->
                    onConnectToFeed(feed.host, pinInput)
                    showConnectionSheet = false
                },
                onConnectManual = {
                    onConnectToFeed(manualIpInput.trim(), pinInput.trim())
                    showConnectionSheet = false
                },
                onDismiss = { showConnectionSheet = false }
            )
        }

        // Disconnect confirmation dialog when leaving connected Remote
        if (showDisconnectConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirmDialog = false },
                title = { Text("Disconnect from Feed?") },
                text = { Text("Disconnect from the current Feed session and return to the Start screen?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDisconnectConfirmDialog = false
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.testTag("confirm_disconnect_button")
                    ) {
                        Text("Disconnect")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDisconnectConfirmDialog = false },
                        modifier = Modifier.testTag("cancel_disconnect_button")
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1E2232),
                titleContentColor = Color.White,
                textContentColor = Color(0xFFCBD5E1)
            )
        }
    }
}

/**
 * Top Bar matching the image:
 * Left: [Connected] green indicator + back icon
 * Center: STREAM CAPTURE
 * Right: Setup gear
 */
@Composable
private fun RemoteTopBar(
    connectionStatus: ConnectionStatus,
    latencyMs: Long,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back button + Connected status indicator
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("remote_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            val statusColor = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> Color(0xFF22C55E)
                ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                ConnectionStatus.SEARCHING -> Color(0xFF3B82F6)
                ConnectionStatus.LOST -> Color(0xFFEF4444)
            }

            val statusLabel = when (connectionStatus) {
                ConnectionStatus.CONNECTED -> "[Connected]"
                ConnectionStatus.CONNECTING -> "[Connecting]"
                ConnectionStatus.SEARCHING -> "[Searching]"
                ConnectionStatus.LOST -> "[Lost]"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onOpenSetup)
            ) {
                Surface(
                    shape = CircleShape,
                    color = statusColor,
                    modifier = Modifier.size(7.dp)
                ) {}
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Center: Title matching image
        Text(
            text = "STREAM CAPTURE",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.2.sp,
                fontSize = 14.sp
            )
        )

        // Right: Setup gear icon button matching image
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (connectionStatus == ConnectionStatus.CONNECTED && latencyMs > 0) {
                Text(
                    text = "${latencyMs}ms",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (latencyMs < 30) Color(0xFF34D399) else Color(0xFFFBBF24),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else if (connectionStatus == ConnectionStatus.LOST) {
                IconButton(
                    onClick = onReconnect,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reconnect",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenSetup)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Setup",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Setup",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFCBD5E1),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Audio Channel Strip Card matching the reference image:
 * - Rounded card container (#161922 to #11131A)
 * - Decibel scale labels: 0, -5, -12, -16, -18, -20, -30, -40, -50, -60
 * - Discrete segmented LED ladder VU meter (Cyan for MIC/AUX, Amber for GAME AUDIO)
 * - Vertical slider track with illuminated glow line & 3D thumb knob with center indicator
 * - Bottom illuminated glowing icon (Mic / Gamepad) & channel label
 */
@Composable
private fun AudioChannelStrip(
    title: String,
    isCapture: Boolean,
    isAvailable: Boolean,
    gain: Float,
    isMuted: Boolean,
    meter: Protocol.SourceMeter,
    isListening: Boolean,
    monitorVolume: Float,
    audioStats: RemoteAudioStats?,
    onGainChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleListen: () -> Unit,
    onMonitorVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var localGain by remember { mutableFloatStateOf(gain) }

    if (!isDragging) {
        localGain = gain
    }

    val themeColor = if (isCapture) Color(0xFFF59E0B) else Color(0xFF00E5FF)
    val dimTrackColor = if (isCapture) Color(0xFF22160E) else Color(0xFF101C2B)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131620)
        ),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMuted) Color(0xFFEF4444).copy(alpha = 0.8f) else Color(0xFF242A3B)
        ),
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (isAvailable) 1f else 0.45f)
            .testTag("channel_strip_${title.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main body: Decibel numbers + Segmented LED VU Meter + Vertical Fader
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decibel Labels Column
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    val dbSteps = listOf(0, -5, -12, -16, -18, -20, -30, -40, -50, -60)
                    dbSteps.forEach { db ->
                        Text(
                            text = "$db",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Segmented LED Ladder VU Meter
                SegmentedLedLadder(
                    rmsDb = meter.rms,
                    peakDb = meter.peak,
                    isAmber = isCapture,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(14.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Decibel reading beside ladder
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start
                ) {
                    val dbSteps = listOf(0, -5, -12, -16, -18, -20, -30, -40, -50, -60)
                    dbSteps.forEach { db ->
                        Text(
                            text = "$db",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF475569),
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Vertical Physical Fader
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    HardwareStyleVerticalFader(
                        value = localGain,
                        enabled = isAvailable,
                        accentColor = themeColor,
                        onValueChange = {
                            isDragging = true
                            localGain = it
                            onGainChange(it)
                        },
                        onDragEnd = {
                            isDragging = false
                            onGainChange(localGain)
                        },
                        tag = "fader_${title.lowercase()}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Channel Section: Glowing Icon & Channel Label (Tap to toggle mute)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = isAvailable, onClick = onToggleMute)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Illuminated icon with colored halo glow
                Surface(
                    shape = CircleShape,
                    color = if (isMuted) Color(0xFF7F1D1D) else themeColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isMuted) {
                                if (isCapture) Icons.Default.VolumeMute else Icons.Default.MicOff
                            } else {
                                if (isCapture) Icons.Default.SportsEsports else Icons.Default.Mic
                            },
                            contentDescription = if (isMuted) "Muted" else title,
                            tint = if (isMuted) Color(0xFFF87171) else themeColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isMuted) Color(0xFFEF4444) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.8.sp
                    )
                )

                if (isMuted) {
                    Text(
                        text = "MUTED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    )
                }
            }

            // Optional headphone monitoring for capture
            if (isCapture) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = if (isListening) Color(0xFF1E3A8A) else Color(0xFF191F2D),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isListening) Color(0xFF60A5FA) else Color(0xFF263044)
                    ),
                    modifier = Modifier
                        .clickable(enabled = isAvailable, onClick = onToggleListen)
                        .testTag("listen_toggle_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Listen",
                            tint = if (isListening) Color(0xFF93C5FD) else Color(0xFF94A3B8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isListening) "MONITOR ON" else "MONITOR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Segmented LED ladder VU meter with 20 discrete illuminated bars, matching the image:
 * Cyan bars for Mic/Aux, Amber bars for Game Audio, top segments turn Red for peak clip.
 */
@Composable
private fun SegmentedLedLadder(
    rmsDb: Float,
    peakDb: Float,
    isAmber: Boolean,
    modifier: Modifier = Modifier
) {
    val totalBars = 20
    val targetFrac = ((rmsDb - (-60f)) / 60f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0C0E14))
            .padding(vertical = 2.dp, horizontal = 1.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Render from top (0 dB) down to bottom (-60 dB)
        for (i in (totalBars - 1) downTo 0) {
            val barFrac = i.toFloat() / (totalBars - 1).toFloat()
            val isLit = barFrac <= targetFrac

            val litColor = when {
                i >= 18 -> Color(0xFFEF4444) // Top clip zone: Red
                isAmber -> Color(0xFFF59E0B) // Amber
                else -> Color(0xFF00E5FF)    // Vibrant Cyan
            }

            val unlitColor = when {
                isAmber -> Color(0xFF1E150E)
                else -> Color(0xFF0F1824)
            }

            Surface(
                shape = RoundedCornerShape(1.dp),
                color = if (isLit) litColor else unlitColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            ) {}
        }
    }
}

/**
 * Skeuomorphic Vertical Fader:
 * - Recessed dark channel groove
 * - Illuminated active neon track
 * - Physical 3D dark charcoal thumb knob with a glowing horizontal center line
 */
@Composable
private fun HardwareStyleVerticalFader(
    value: Float,
    enabled: Boolean,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    tag: String
) {
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnEnd by rememberUpdatedState(onDragEnd)
    val thumbHeight = 28.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(42.dp)
            .testTag(tag)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var acc = 0f
                detectVerticalDragGestures(
                    onDragStart = { acc = latestValue },
                    onDragEnd = { latestOnEnd() },
                    onDragCancel = { latestOnEnd() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val travel = (size.height - thumbHeight.toPx()).coerceAtLeast(1f)
                        acc = (acc - dragAmount / travel).coerceIn(0f, 1f)
                        latestOnChange(acc)
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val v = value.coerceIn(0f, 1f)
        val travelDp = maxHeight - thumbHeight

        // Recessed vertical groove channel
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0A0C11))
                .border(1.dp, Color(0xFF181C26), RoundedCornerShape(3.dp))
        )

        // Glowing illuminated active track up to the thumb
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(4.dp)
                .height(travelDp * v + thumbHeight / 2)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        // 3D Physical Charcoal Fader Knob with Glowing Horizontal Line
        Surface(
            color = Color(0xFF262B38),
            shape = RoundedCornerShape(7.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3E475B)),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = travelDp * (1f - v))
                .size(width = 38.dp, height = thumbHeight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Horizontal illuminated indicator line matching screenshot
                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

/**
 * 2x3 Shortcuts Grid Section with ACTIVATORS on buttons, and LEFT/RIGHT Paging:
 * - 6 buttons per page in a 2-column x 3-row layout, driven from ShortcutConfig.
 * - Disabled pages are omitted.
 * - Empty slots are left blank rather than filled with fake buttons.
 * - "SOON" tags are shown for unbuilt features.
 */
@Composable
private fun ShortcutGridSection(
    page: Int,
    pages: List<com.example.net.ShortcutConfig.ShortcutPageDef>,
    activeShortcuts: Set<String>,
    micMuted: Boolean,
    localMonitor: Boolean,
    isEnabled: Boolean,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    onButtonClick: (String) -> Unit,
    onSetLocalMonitor: (Boolean) -> Unit,
    onToggleMicMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPages = pages.size
    val safePageIndex = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val currentPageDef = pages.getOrNull(safePageIndex) ?: com.example.net.ShortcutConfig.getEnabledPages().first()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragTotal = 0f },
                    onDragEnd = {
                        if (dragTotal > 50f) onPrevPage()
                        else if (dragTotal < -50f) onNextPage()
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragTotal += dragAmount
                    }
                )
            }
    ) {
        // Page Navigation Header with Left & Right controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left arrow
            IconButton(
                onClick = onPrevPage,
                enabled = safePageIndex > 0,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("shortcuts_prev_page")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous shortcuts",
                    tint = if (safePageIndex > 0) Color(0xFF00E5FF) else Color(0xFF333A4D),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Page indicator dots & label
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (p in 0 until totalPages) {
                    val isCur = p == safePageIndex
                    Surface(
                        shape = CircleShape,
                        color = if (isCur) Color(0xFF00E5FF) else Color(0xFF333A4D),
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (isCur) 6.dp else 4.dp)
                    ) {}
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${currentPageDef.title}  •  PAGE ${safePageIndex + 1} OF $totalPages",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                )
            }

            // Right arrow
            IconButton(
                onClick = onNextPage,
                enabled = safePageIndex < totalPages - 1,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("shortcuts_next_page")
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "More shortcuts",
                    tint = if (safePageIndex < totalPages - 1) Color(0xFF00E5FF) else Color(0xFF333A4D),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 2 columns x 3 rows grid of buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0..2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (col in 0..1) {
                        val itemIndex = row * 2 + col
                        val shortcut = currentPageDef.buttons.getOrNull(itemIndex)

                        if (shortcut != null) {
                            val isSelected = when (shortcut.id) {
                                com.example.net.ShortcutConfig.ACTION_MUTE_MIC -> micMuted
                                com.example.net.ShortcutConfig.ACTION_LISTEN_MONITOR -> localMonitor
                                else -> activeShortcuts.contains(shortcut.id)
                            }

                            ShortcutActivatorButton(
                                id = shortcut.id,
                                title = shortcut.label,
                                glowColor = Color(shortcut.glowColorArgb),
                                isSelected = isSelected,
                                isDimmed = shortcut.isSoon || (!shortcut.isClickable && !isSelected),
                                isSoon = shortcut.isSoon,
                                isEnabled = isEnabled && shortcut.isClickable,
                                onClick = {
                                    if (shortcut.isSoon) {
                                        // Dimmed and non-clickable with SOON tag, don't send any action
                                    } else if (shortcut.id == com.example.net.ShortcutConfig.ACTION_LISTEN_MONITOR) {
                                        onSetLocalMonitor(!localMonitor)
                                    } else if (shortcut.id == com.example.net.ShortcutConfig.ACTION_MUTE_MIC) {
                                        onToggleMicMute()
                                    } else {
                                        onButtonClick(shortcut.id)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        } else {
                            // Empty slot: leave empty, not filled with fake buttons
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Capsule button with an illuminated neon border activator and active state indicator
 * matching the buttons in the reference screenshot!
 */
@Composable
private fun ShortcutActivatorButton(
    id: String,
    title: String,
    glowColor: Color,
    isSelected: Boolean,
    isDimmed: Boolean,
    isSoon: Boolean = false,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f)

    val borderColor = when {
        isSelected -> glowColor
        isDimmed -> Color(0xFF222838)
        else -> glowColor.copy(alpha = 0.85f)
    }

    val borderWidth = if (isSelected) 2.dp else 1.5.dp

    Surface(
        color = when {
            isSelected -> glowColor.copy(alpha = 0.12f)
            isDimmed -> Color(0xFF0F121A)
            else -> Color(0xFF141722)
        },
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .scale(scale)
            .clickable(enabled = isEnabled) {
                isPressed = true
                onClick()
                isPressed = false
            }
            .testTag("shortcut_button_$id")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            // "SOON" tag for placeholder buttons
            if (isSoon) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF262D3D),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                ) {
                    Text(
                        text = "SOON",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            } else if (isSelected) {
                // Activator LED indicator on top-right when selected
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = glowColor,
                        modifier = Modifier.size(6.dp)
                    ) {}
                }
            }

            // Button Title in bold all-caps
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isSelected -> Color.White
                        isDimmed -> Color(0xFF4A5568)
                        else -> Color(0xFFE2E8F0)
                    },
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}

/**
 * Sponsor / Studio status bottom capsule matching the reference image:
 * [AD] SPONSOR AD PLACEHOLDER - GET 20% OFF NOW! - Learn More >
 */
@Composable
private fun SponsorBottomCapsule(
    onOpenSetup: () -> Unit
) {
    Surface(
        color = Color(0xFF131722),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSetup)
            .testTag("sponsor_bottom_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // AD Tag badge
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64748B)),
                    modifier = Modifier.padding(end = 10.dp)
                ) {
                    Text(
                        text = "AD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Column {
                    Text(
                        text = "SPONSOR AD PLACEHOLDER",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "GET 20% OFF NOW! - Learn More",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Details",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionBottomSheet(
    discoveredFeeds: List<DiscoveredFeed>,
    manualIp: String,
    pin: String,
    onManualIpChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onSelectDiscovered: (DiscoveredFeed) -> Unit,
    onConnectManual: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161824),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Connect to Feed",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = pin,
                onValueChange = onPinChange,
                label = { Text("Feed Pairing PIN (4-digit)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_input_field")
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Discovered Feeds on Wi-Fi:",
                style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF94A3B8))
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (discoveredFeeds.isEmpty()) {
                Surface(
                    color = Color(0xFF1E2232),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Searching for nearby Feeds via mDNS...",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discoveredFeeds.forEach { feed ->
                        Surface(
                            color = Color(0xFF1E2436),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDiscovered(feed) }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = feed.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = "${feed.host}:${feed.port}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF94A3B8),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                                Button(
                                    onClick = { onSelectDiscovered(feed) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Manual Connection (Router Fallback):",
                style = MaterialTheme.typography.labelMedium.copy(color = Color(0xFF94A3B8))
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = onManualIpChange,
                    label = { Text("Feed IP Address") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_ip_input_field")
                )

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onConnectManual,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("manual_connect_button")
                ) {
                    Text("Connect")
                }
            }
        }
    }
}
