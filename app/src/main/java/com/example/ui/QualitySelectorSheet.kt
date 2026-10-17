package com.example.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.capture.QualityMode
import com.example.player.PlayerStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelectorSheet(
    modes: List<QualityMode>,
    currentMode: QualityMode,
    stats: PlayerStats,
    onSelectMode: (QualityMode) -> Unit,
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
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Quality Selector",
                        tint = Color(0xFF64B5F6),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Video Quality & Modes",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_quality_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFFAAAAAA)
                    )
                }
            }

            // Real-time FPS telemetry bar
            Surface(
                color = Color(0xFF222634),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "FPS Telemetry",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "FPS Telemetry",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color(0xFFB0B0B0),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Text(
                        text = "Req: ${stats.requestedFps} | Neg: ${stats.negotiatedFps} | Meas: ${String.format("%.1f", stats.captureFps)}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64B5F6),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Text(
                text = "Select capture resolution and frame rate. Hardware MJPEG modes are read directly from the capture card:",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF9E9E9E)),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Mode choices list
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                modes.forEach { mode ->
                    val isSelected = mode.id == currentMode.id
                    val isSupported = mode.isSupportedByCard

                    QualityModeItem(
                        mode = mode,
                        isSelected = isSelected,
                        isSupported = isSupported,
                        onClick = {
                            if (isSupported && !isSelected) {
                                onSelectMode(mode)
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityModeItem(
    mode: QualityMode,
    isSelected: Boolean,
    isSupported: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> Color(0xFF1E2D4A)
        isSupported -> Color(0xFF1F2230)
        else -> Color(0xFF191A22)
    }

    val borderColor = when {
        isSelected -> Color(0xFF42A5F5)
        isSupported -> Color(0xFF2E3244)
        else -> Color(0xFF282830)
    }

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
            .clickable(enabled = isSupported, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("quality_item_${mode.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSupported) Color.White else Color(0xFF6B6E7E)
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${mode.targetWidth}x${mode.targetHeight}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isSupported) Color(0xFF90CAF9) else Color(0xFF555562),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }

            if (!isSupported) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Unsupported",
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = mode.reasonUnsupported ?: "Not supported by card",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFFE57373),
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
                modifier = Modifier.size(22.dp)
            )
        } else if (!isSupported) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF2C2224),
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Unavailable",
                    tint = Color(0xFF8B5558),
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}
