package com.example.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EulaScreen(
    readOnly: Boolean = false,
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // The user can accept only when scrolled to the very bottom (with small tolerance)
    val isScrolledToBottom by remember {
        derivedStateOf {
            scrollState.maxValue == 0 || scrollState.value >= (scrollState.maxValue - 24)
        }
    }

    // In mandatory gate mode, back press closes the app entirely (never skips the gate)
    BackHandler {
        if (readOnly) {
            onDismiss()
        } else {
            (context as? Activity)?.finishAffinity()
        }
    }

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
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Text(
                text = "End User Licence Agreement",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                modifier = Modifier.testTag("eula_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Version $LEGAL_TERMS_VERSION — Effective [DATE]",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.SemiBold
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF262C3D))
            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = 16.dp)
                        .testTag("eula_scroll_container")
                ) {
                    val sections = LEGAL_TERMS_TEXT.split("\n\n")
                    sections.forEach { section ->
                        val trimmed = section.trim()
                        if (trimmed.isNotEmpty()) {
                            // Check if this block starts with a numbered heading (e.g., "1. PARTIES")
                            val isNumberedHeading = trimmed.firstOrNull()?.isDigit() == true && trimmed.contains(". ")
                            val isAllCapNotice = trimmed.startsWith("PLEASE READ") || trimmed.startsWith("END USER")

                            if (isNumberedHeading) {
                                val lines = trimmed.split("\n", limit = 2)
                                val heading = lines.getOrNull(0) ?: ""
                                val body = lines.getOrNull(1) ?: ""

                                Text(
                                    text = heading,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 15.sp
                                    ),
                                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                                )
                                if (body.isNotEmpty()) {
                                    Text(
                                        text = body,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color(0xFF94A3B8),
                                            lineHeight = 21.sp,
                                            fontSize = 13.5.sp
                                        )
                                    )
                                }
                            } else {
                                Text(
                                    text = trimmed,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isAllCapNotice) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                        fontWeight = if (isAllCapNotice) FontWeight.SemiBold else FontWeight.Normal,
                                        lineHeight = 21.sp,
                                        fontSize = 13.5.sp
                                    ),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Action Area
            Surface(
                color = Color(0xFF141824),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (readOnly) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E5FF),
                                contentColor = Color(0xFF0A101D)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("eula_close_button")
                        ) {
                            Text("CLOSE", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        if (!isScrolledToBottom) {
                            Text(
                                text = "Scroll to the bottom to accept",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Medium
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(bottom = 10.dp)
                                    .testTag("eula_scroll_hint")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    (context as? Activity)?.finishAffinity()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFEF4444)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("eula_decline_button")
                            ) {
                                Text("DECLINE", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = onAccept,
                                enabled = isScrolledToBottom,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF),
                                    contentColor = Color(0xFF0A101D),
                                    disabledContainerColor = Color(0xFF1E2538),
                                    disabledContentColor = Color(0xFF4B5563)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("eula_accept_button")
                            ) {
                                Text("I ACCEPT", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
