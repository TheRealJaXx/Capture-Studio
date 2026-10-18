package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerStats

@Composable
fun StatsOverlay(
    stats: PlayerStats,
    resolution: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .testTag("stats_overlay")
                .widthIn(min = 180.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "CAPTURE STUDIO HUD",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }

            StatRow(
                label = "Resolution",
                value = resolution
            )
            StatRow(
                label = "Capture FPS",
                value = String.format("%.1f fps", stats.captureFps),
                highlight = stats.captureFps >= (stats.requestedFps - 5)
            )
            StatRow(
                label = "Negotiated",
                value = "${stats.negotiatedFps} fps"
            )
            StatRow(
                label = "Requested",
                value = "${stats.requestedFps} fps"
            )
            StatRow(
                label = "Decode Time",
                value = "${stats.decodeTimeMs} ms",
                highlight = stats.decodeTimeMs < 16
            )
            StatRow(
                label = "Frame Latency",
                value = "${stats.frameAgeMs} ms",
                highlight = stats.frameAgeMs < 30
            )
            StatRow(
                label = "Dropped",
                value = "${stats.droppedFrames}",
                warning = stats.droppedFrames > 0
            )
            if (stats.audioStatus.isNotEmpty()) {
                Text(
                    text = stats.audioStatus,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFFBBF24),
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    warning: Boolean = false
) {
    Row(
        modifier = Modifier.widthIn(min = 180.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = Color(0xFFAAAAAA),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = when {
                    warning -> Color(0xFFFFB74D)
                    highlight -> Color(0xFF81C784)
                    else -> Color.White
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}
