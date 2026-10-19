package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

enum class CameraLayout(val label: String, val isSplit: Boolean = false) {
    OFF("Camera Off", isSplit = false),
    PIP_TOP_LEFT("PiP Top Left", isSplit = false),
    PIP_TOP_RIGHT("PiP Top Right", isSplit = false),
    PIP_BOTTOM_LEFT("PiP Bottom Left", isSplit = false),
    PIP_BOTTOM_RIGHT("PiP Bottom Right", isSplit = false),
    SPLIT_CAMERA_TOP("Split Top", isSplit = true),
    SPLIT_CAMERA_BOTTOM("Split Bottom", isSplit = true);

    fun next(): CameraLayout {
        val all = values()
        val nextIndex = (ordinal + 1) % all.size
        return all[nextIndex]
    }
}

@Composable
fun CameraPip(
    front: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = activity as? LifecycleOwner

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraFailed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(Color(0xFF161B2C))
            .testTag("camera_pip_container"),
        contentAlignment = Alignment.Center
    ) {
        if (!hasPermission) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "Camera Permission Required",
                    tint = Color(0xFFFCA5A5),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Camera Permission",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { launcher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .height(24.dp)
                        .testTag("grant_camera_pip_permission_button"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text("Grant", fontSize = 9.sp)
                }
            }
        } else if (cameraFailed) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Unavailable",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Camera Unavailable",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFCBD5E1),
                        fontSize = 9.sp
                    )
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    if (lifecycleOwner != null) {
                        try {
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    cameraProvider.unbindAll()

                                    val preview = Preview.Builder().build()
                                    preview.setSurfaceProvider(previewView.surfaceProvider)

                                    val selector = if (front) {
                                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        }
                                    } else {
                                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        }
                                    }

                                    cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                                    cameraFailed = false
                                } catch (e: Throwable) {
                                    Log.e("CameraPip", "Failed to bind CameraX preview: ${e.message}", e)
                                    cameraFailed = true
                                }
                            }, ContextCompat.getMainExecutor(previewView.context))
                        } catch (e: Throwable) {
                            Log.e("CameraPip", "ProcessCameraProvider error: ${e.message}", e)
                            cameraFailed = true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camerax_preview_view")
            )

            DisposableEffect(front, lifecycleOwner) {
                onDispose {
                    try {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }
}
