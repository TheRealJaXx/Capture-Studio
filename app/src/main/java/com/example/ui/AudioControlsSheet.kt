package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioInputDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioControlsSheet(
    devices: List<AudioInputDevice>,
    selectedDevice: AudioInputDevice?,
    volume: Float,
    isMuted: Boolean,
    localMonitorEnabled: Boolean = true,
    onSelectDevice: (AudioInputDevice) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLocalMonitor: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF161822),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "Audio Controls",
                        tint = Color(0xFF81C784),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Audio Monitor & Routing",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_audio_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }

            // Volume & Mute Controls Card
            Surface(
                color = Color(0xFF202330),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Monitor Volume: ${(volume * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isMuted) Color(0xFFEF5350) else Color.White
                            )
                        )

                        FilledTonalButton(
                            onClick = onToggleMute,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (isMuted) Color(0xFF4E2024) else Color(0xFF2C394E),
                                contentColor = if (isMuted) Color(0xFFFF8A80) else Color(0xFF90CAF9)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Unmute" else "Mute",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isMuted) "MUTED" else "UNMUTE")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF81C784),
                            activeTrackColor = Color(0xFF66BB6A),
                            inactiveTrackColor = Color(0xFF333842)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("volume_slider")
                    )

                    // Local Monitor Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .clickable { onToggleLocalMonitor() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speaker,
                                contentDescription = null,
                                tint = if (localMonitorEnabled) Color(0xFF60A5FA) else Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Feed Local Monitor",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = if (localMonitorEnabled) "Playing through phone speaker" else "Muted locally (wireless stream active)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = localMonitorEnabled,
                            onCheckedChange = { onToggleLocalMonitor() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF60A5FA),
                                checkedTrackColor = Color(0xFF1E3A8A)
                            ),
                            modifier = Modifier.testTag("local_monitor_toggle")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Anti-Feedback Guard",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Acoustic anti-feedback protection active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFA5D6A7),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Warning if built-in mic selected
            AnimatedVisibility(visible = selectedDevice?.isBuiltInMic == true) {
                Surface(
                    color = Color(0xFF3E2723),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Feedback Warning",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Acoustic Howl-Round Warning",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFCC80)
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Using the phone microphone while loudspeaker is on can cause severe acoustic feedback howling. Monitor has been muted automatically.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFFFE0B2),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            Text(
                text = "Available Input Devices (Defaults to HDMI USB Capture Audio):",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9E9E9E)),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Audio Input Devices List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (devices.isEmpty()) {
                    Text(
                        text = "No audio input devices detected",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    devices.forEach { device ->
                        val isSelected = device.id == selectedDevice?.id

                        AudioDeviceItem(
                            device = device,
                            isSelected = isSelected,
                            onClick = {
                                onSelectDevice(device)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioDeviceItem(
    device: AudioInputDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF1E2D4A) else Color(0xFF1F2230)
    val borderColor = if (isSelected) Color(0xFF42A5F5) else Color(0xFF2E3244)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("audio_device_${device.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = when {
                    device.isUsb -> Icons.Default.Usb
                    device.isBuiltInMic -> Icons.Default.Mic
                    else -> Icons.Default.Headphones
                },
                contentDescription = null,
                tint = when {
                    device.isUsb -> Color(0xFF81C784)
                    device.isBuiltInMic -> Color(0xFFFFB74D)
                    else -> Color(0xFF90CAF9)
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = Color.White
                    )
                )
                if (device.isUsb) {
                    Text(
                        text = "Preferred HDMI Capture Source (Low Latency)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFA5D6A7),
                            fontSize = 11.sp
                        )
                    )
                } else if (device.isBuiltInMic) {
                    Text(
                        text = "Phone internal mic • Starts muted to avoid howling",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFFFCC80),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = Color(0xFF42A5F5),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
