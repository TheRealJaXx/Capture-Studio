package com.example.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Canvas-based vertical audio level meter with fast attack, smooth release,
 * peak hold tick, and 6 dB scale marks.
 */
@Composable
fun LevelMeter(
    rmsDb: Float,
    peakDb: Float,
    modifier: Modifier = Modifier,
    tag: String = "level_meter"
) {
    // Current animated level fractions [0f .. 1f]
    var currentRmsFrac by remember { mutableFloatStateOf(0f) }
    var currentPeakHoldFrac by remember { mutableFloatStateOf(0f) }
    var peakHoldTimestamp by remember { mutableFloatStateOf(0f) }

    val targetRmsFrac = ((rmsDb - (-60f)) / 60f).coerceIn(0f, 1f)
    val targetPeakFrac = ((peakDb - (-60f)) / 60f).coerceIn(0f, 1f)

    // Ballistics loop: fast attack, slow release, peak hold drop
    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameTime ->
                val dt = if (lastTime == 0L) 16f else (frameTime - lastTime).coerceAtMost(50L).toFloat()
                lastTime = frameTime

                // RMS Ballistics: instant attack, ~300ms decay
                if (targetRmsFrac >= currentRmsFrac) {
                    currentRmsFrac = targetRmsFrac // instant attack
                } else {
                    val decayRate = dt / 350f
                    currentRmsFrac = max(0f, currentRmsFrac - decayRate)
                }

                // Peak Hold Ballistics: hold 800ms, then drop slowly
                if (targetPeakFrac >= currentPeakHoldFrac) {
                    currentPeakHoldFrac = targetPeakFrac
                    peakHoldTimestamp = frameTime.toFloat()
                } else {
                    if (frameTime - peakHoldTimestamp > 800) {
                        val peakDropRate = dt / 400f
                        currentPeakHoldFrac = max(0f, currentPeakHoldFrac - peakDropRate)
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .testTag(tag)
            .width(28.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .padding(vertical = 4.dp)
        ) {
            val w = size.width
            val h = size.height

            // Background trough
            drawRoundRect(
                color = Color(0xFF141720),
                topLeft = Offset(0f, 0f),
                size = Size(10.dp.toPx(), h),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Dynamic Gradient: Green (<= -18dB), Yellow (-18dB .. -6dB), Red (> -6dB)
            // Note: 0dB is top (y=0), -6dB is at 10% down, -18dB is at 30% down, -60dB is at 100% down
            val meterGradient = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color(0xFFEF4444), // 0 dB (Red)
                    0.10f to Color(0xFFEF4444), // -6 dB (Red/Orange)
                    0.12f to Color(0xFFEAB308), // Yellow boundary
                    0.30f to Color(0xFFEAB308), // -18 dB (Yellow)
                    0.35f to Color(0xFF22C55E), // Green boundary
                    1.0f to Color(0xFF16A34A)  // -60 dB (Green)
                ),
                startY = 0f,
                endY = h
            )

            // Active Level Bar
            val activeHeight = h * currentRmsFrac
            if (activeHeight > 0f) {
                val barTop = h - activeHeight
                drawRoundRect(
                    brush = meterGradient,
                    topLeft = Offset(0f, barTop),
                    size = Size(10.dp.toPx(), activeHeight),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            // Peak Hold Tick mark
            if (currentPeakHoldFrac > 0.02f) {
                val peakY = (h * (1f - currentPeakHoldFrac)).coerceIn(1f, h - 2f)
                val peakColor = when {
                    currentPeakHoldFrac > 0.90f -> Color(0xFFFF5252)
                    currentPeakHoldFrac > 0.70f -> Color(0xFFFFD54F)
                    else -> Color(0xFF81C784)
                }
                drawLine(
                    color = peakColor,
                    start = Offset(0f, peakY),
                    end = Offset(10.dp.toPx(), peakY),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // Draw tick marks every 6 dB (0, -6, -12, -18, -24, -30, -36, -42, -48, -54, -60)
            val tickX = 14.dp.toPx()
            for (db in 0 downTo -60 step 6) {
                val frac = ((db - (-60f)) / 60f).coerceIn(0f, 1f)
                val y = h * (1f - frac)
                val isMajor = db == 0 || db == -6 || db == -18

                val tickLen = if (isMajor) 6.dp.toPx() else 3.dp.toPx()
                val tickColor = when {
                    db >= -6 -> Color(0xFFEF4444).copy(alpha = 0.8f)
                    db >= -18 -> Color(0xFFEAB308).copy(alpha = 0.8f)
                    else -> Color(0xFF64748B)
                }

                drawLine(
                    color = tickColor,
                    start = Offset(tickX, y),
                    end = Offset(tickX + tickLen, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
