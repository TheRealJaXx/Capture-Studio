package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SouthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraSettingsSheet(
    cameraLayout: CameraLayout,
    cameraPipSize: CameraPipSize,
    cameraAspectRatio: CameraAspectRatio,
    cameraBorder: Boolean,
    cameraFront: Boolean,
    isCameraOnly: Boolean = false,
    onSelectLayout: (CameraLayout) -> Unit,
    onSelectSize: (CameraPipSize) -> Unit,
    onSelectAspectRatio: (CameraAspectRatio) -> Unit,
    onToggleBorder: (Boolean) -> Unit,
    onFlipCamera: () -> Unit,
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
                .padding(bottom = 28.dp)
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
                        contentDescription = "Camera Settings",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Camera & PiP Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "Layout, sizing, aspect crop and border",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_camera_settings_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF272B3B))
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Layout Mode
            Text(
                text = "CAMERA LAYOUT",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF93C5FD),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CameraLayout.values().forEach { layout ->
                    val isSelected = cameraLayout == layout
                    val icon: ImageVector = when (layout) {
                        CameraLayout.OFF -> Icons.Default.VideocamOff
                        CameraLayout.PIP_TOP_LEFT -> Icons.Default.NorthWest
                        CameraLayout.PIP_TOP_RIGHT -> Icons.Default.NorthEast
                        CameraLayout.PIP_BOTTOM_LEFT -> Icons.Default.SouthWest
                        CameraLayout.PIP_BOTTOM_RIGHT -> Icons.Default.SouthEast
                        CameraLayout.SPLIT_CAMERA_TOP -> Icons.Default.VerticalAlignTop
                        CameraLayout.SPLIT_CAMERA_BOTTOM -> Icons.Default.VerticalAlignBottom
                        CameraLayout.FULL_SCREEN -> Icons.Default.FitScreen
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E2230),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF60A5FA) else Color(0xFF333A4D)
                        ),
                        modifier = Modifier
                            .clickable { onSelectLayout(layout) }
                            .testTag("select_camera_layout_${layout.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = layout.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. PiP Size (Small / Medium / Large)
            if (!cameraLayout.isSplit && cameraLayout != CameraLayout.OFF && cameraLayout != CameraLayout.FULL_SCREEN) {
                Text(
                    text = "PIP SIZE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF93C5FD),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CameraPipSize.values().forEach { size ->
                        val isSelected = cameraPipSize == size
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E2230),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF60A5FA) else Color(0xFF333A4D)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectSize(size) }
                                .testTag("select_camera_pip_size_${size.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = size.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${(size.widthFraction * 100).toInt()}% width",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color(0xFFBFDBFE) else Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 3. Aspect Ratio (16:9, 4:3, 1:1, 9:16)
            if (cameraLayout != CameraLayout.OFF) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ASPECT RATIO (CROPPED)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "No stretch / auto crop",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CameraAspectRatio.values().forEach { aspect ->
                        val isSelected = cameraAspectRatio == aspect
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E2230),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF60A5FA) else Color(0xFF333A4D)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectAspectRatio(aspect) }
                                .testTag("select_camera_aspect_${aspect.name}")
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = aspect.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = aspect.description,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color(0xFFBFDBFE) else Color(0xFF94A3B8),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // 4. White Border & Lens Flipping
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Border Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2230),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A4D)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleBorder(!cameraBorder) }
                        .testTag("toggle_camera_border_tile")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Camera Border",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (cameraBorder) "White frame" else "No border",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (cameraBorder) Color(0xFF93C5FD) else Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Switch(
                            checked = cameraBorder,
                            onCheckedChange = onToggleBorder,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.testTag("camera_border_switch")
                        )
                    }
                }

                // Flip Camera Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E2230),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A4D)),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onFlipCamera() }
                        .testTag("flip_camera_tile")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Camera Lens",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = if (cameraFront) "Front (Selfie)" else "Back camera",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF93C5FD),
                                    fontSize = 11.sp
                                )
                            )
                        }
                        IconButton(
                            onClick = onFlipCamera,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Flip Lens",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
