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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
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
import kotlin.math.roundToInt

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
    onButtonClick: (Int) -> Unit,
    onToggleListen: () -> Unit,
    onSetMonitorVolume: (Float) -> Unit,
    onConnectToFeed: (String, String) -> Unit,
    onReconnect: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    BackHandler {
        onNavigateBack()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F111A))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // 1. Slim Status Bar
            RemoteStatusBar(
                connectionStatus = connectionStatus,
                connectedIp = connectedFeedIp,
                latencyMs = latencyMs,
                onBack = onNavigateBack,
                onOpenConnectionSheet = { showConnectionSheet = true },
                onReconnect = onReconnect
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Dual Channel Audio Section (MIC and CAPTURE)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.15f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // MIC Channel Strip
                AudioChannelStrip(
                    title = "MIC",
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

                // CAPTURE Channel Strip
                AudioChannelStrip(
                    title = "CAPTURE",
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

            // 3. Shortcut Section (2x3 Grid of 6 buttons)
            ShortcutGridSection(
                buttonLabels = feedState.buttonLabels,
                isEnabled = isConnected,
                onButtonClick = { id ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onButtonClick(id)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.85f)
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
    }
}

@Composable
private fun RemoteStatusBar(
    connectionStatus: ConnectionStatus,
    connectedIp: String?,
    latencyMs: Long,
    onBack: () -> Unit,
    onOpenConnectionSheet: () -> Unit,
    onReconnect: () -> Unit
) {
    Surface(
        color = Color(0xFF161824),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF262C3E)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("remote_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Connection State Pill
                val statusColor = when (connectionStatus) {
                    ConnectionStatus.CONNECTED -> Color(0xFF10B981)
                    ConnectionStatus.CONNECTING -> Color(0xFFF59E0B)
                    ConnectionStatus.SEARCHING -> Color(0xFF3B82F6)
                    ConnectionStatus.LOST -> Color(0xFFEF4444)
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable(onClick = onOpenConnectionSheet)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = CircleShape, color = statusColor, modifier = Modifier.size(8.dp)) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = connectionStatus.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = connectedIp ?: "No Feed",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Text(
                        text = "$latencyMs ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (latencyMs < 30) Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(end = 6.dp)
                    )
                } else if (connectionStatus == ConnectionStatus.LOST) {
                    IconButton(
                        onClick = onReconnect,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("remote_reconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reconnect",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onOpenConnectionSheet,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("remote_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Connection Settings",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

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
    // Local fader state to allow fluid dragging without fighting incoming state
    var isDragging by remember { mutableStateOf(false) }
    var localGain by remember { mutableFloatStateOf(gain) }

    if (!isDragging) {
        localGain = gain
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161824)
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMuted) Color(0xFF7F1D1D) else Color(0xFF262C3E)
        ),
        modifier = modifier
            .fillMaxHeight()
            .alpha(if (isAvailable) 1f else 0.45f)
            .testTag("channel_strip_${title.lowercase()}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Strip Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCapture) Color(0xFF60A5FA) else Color(0xFF34D399),
                        letterSpacing = 1.sp
                    )
                )

                // Gain dB readout
                val dbVal = if (localGain <= 0.001f) "-inf dB" else {
                    val db = 20 * log10(localGain)
                    String.format("%+.1f dB", db)
                }
                Text(
                    text = dbVal,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFCBD5E1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (!isAvailable) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unavailable on Feed",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFF87171),
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fader + Live Level Meter side by side
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tall Vertical Fader
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    VerticalFader(
                        value = localGain,
                        enabled = isAvailable,
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

                // Live Level Meter
                LevelMeter(
                    rmsDb = meter.rms,
                    peakDb = meter.peak,
                    modifier = Modifier.fillMaxHeight(),
                    tag = "meter_${title.lowercase()}"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stage 5 Listen Toggle & Local Monitor Slider (CAPTURE channel only)
            if (isCapture) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalButton(
                        onClick = onToggleListen,
                        enabled = isAvailable,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isListening) Color(0xFF1E3A8A) else Color(0xFF1F2433),
                            contentColor = if (isListening) Color(0xFF93C5FD) else Color(0xFF94A3B8)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("listen_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Listen",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isListening) "LISTENING" else "LISTEN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Local Monitor Volume Slider on Remote
                if (isListening) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Slider(
                            value = monitorVolume,
                            onValueChange = onMonitorVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF60A5FA),
                                activeTrackColor = Color(0xFF3B82F6),
                                inactiveTrackColor = Color(0xFF262C3E)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .testTag("remote_monitor_volume_slider")
                        )
                    }

                    // Audio Stream Telemetry
                    audioStats?.let { stats ->
                        Text(
                            text = "Buf: ${stats.bufferDepthMs}ms • Loss: ${String.format("%.1f", stats.packetLossPercent)}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Big MUTE Button
            val muteBgColor by animateColorAsState(
                if (isMuted) Color(0xFFDC2626) else Color(0xFF1E293B)
            )
            val muteTextColor = if (isMuted) Color.White else Color(0xFF94A3B8)

            Button(
                onClick = onToggleMute,
                enabled = isAvailable,
                colors = ButtonDefaults.buttonColors(
                    containerColor = muteBgColor,
                    contentColor = muteTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("mute_button_${title.lowercase()}")
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        if (isCapture) Icons.Default.VolumeMute else Icons.Default.MicOff
                    } else {
                        if (isCapture) Icons.Default.VolumeUp else Icons.Default.Mic
                    },
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isMuted) "MUTED" else "MUTE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun VerticalFader(
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onDragEnd: () -> Unit,
    tag: String
) {
    // Always read the latest values inside the long-lived pointerInput block
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnEnd by rememberUpdatedState(onDragEnd)
    val thumbHeight = 26.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxHeight()
            .width(44.dp)
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

        // Trough
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF141720))
        )

        // Active track: from the bottom up to the thumb centre
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(6.dp)
                .height(travelDp * v + thumbHeight / 2)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF3B82F6))
        )

        // Fader cap: moves with the value (top = full, bottom = silent)
        Surface(
            color = Color(0xFFE2E8F0),
            shape = RoundedCornerShape(6.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF94A3B8)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = travelDp * (1f - v))
                .size(width = 38.dp, height = thumbHeight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height(2.dp)
                        .background(Color(0xFF475569))
                )
            }
        }
    }
}

@Composable
private fun ShortcutGridSection(
    buttonLabels: List<String>,
    isEnabled: Boolean,
    onButtonClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161824))
            .border(1.dp, Color(0xFF262C3E), RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Text(
            text = "SHORTCUT ACTIONS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            ),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        // 2 rows of 3 buttons
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (id in 1..3) {
                    val label = buttonLabels.getOrNull(id - 1) ?: "Button $id"
                    ShortcutDeckButton(
                        id = id,
                        label = label,
                        isEnabled = isEnabled,
                        onClick = { onButtonClick(id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (id in 4..6) {
                    val label = buttonLabels.getOrNull(id - 1) ?: "Button $id"
                    ShortcutDeckButton(
                        id = id,
                        label = label,
                        isEnabled = isEnabled,
                        onClick = { onButtonClick(id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutDeckButton(
    id: Int,
    label: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f)

    Surface(
        color = if (isEnabled) Color(0xFF1E2436) else Color(0xFF141722),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPressed) Color(0xFF60A5FA) else Color(0xFF2E374E)
        ),
        modifier = modifier
            .fillMaxHeight()
            .scale(scale)
            .clickable(enabled = isEnabled) {
                isPressed = true
                onClick()
                isPressed = false
            }
            .testTag("shortcut_button_$id")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2B354F),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$id",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isEnabled) Color.White else Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
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

            // Pairing PIN input field
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
