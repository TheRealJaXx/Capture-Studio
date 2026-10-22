package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
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
import androidx.compose.material.icons.filled.Cameraswitch
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    SPLIT_CAMERA_BOTTOM("Split Bottom", isSplit = true),
    FULL_SCREEN("Full Screen", isSplit = false);

    fun next(): CameraLayout {
        val all = values()
        val nextIndex = (ordinal + 1) % all.size
        return all[nextIndex]
    }
}

enum class CameraPipSize(val label: String, val widthFraction: Float) {
    SMALL("Small", 0.18f),
    MEDIUM("Medium", 0.24f),
    LARGE("Large", 0.30f);

    fun next(): CameraPipSize {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

enum class CameraAspectRatio(
    val label: String,
    val ratio: Float,
    val description: String
) {
    RATIO_16_9("16:9", 16f / 9f, "Widescreen"),
    RATIO_4_3("4:3", 4f / 3f, "Standard"),
    RATIO_1_1("1:1", 1f, "Square"),
    RATIO_9_16("9:16", 9f / 16f, "Vertical");

    fun next(): CameraAspectRatio {
        val all = values()
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * Manages CameraX bindings for both primary (full screen) and secondary (PiP) cameras.
 * Supports concurrent dual-camera streaming on capable hardware, and falls back gracefully.
 */
class StudioCameraState(
    val context: Context,
    val lifecycleOwner: LifecycleOwner?
) {
    var isConcurrentSupported by mutableStateOf(false)
        private set
    var cameraFailed by mutableStateOf(false)
        private set

    val primaryPreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    val secondaryPreviewView = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    fun updateBinding(
        hasUsbCapture: Boolean,
        mainFront: Boolean,
        cameraLayout: CameraLayout
    ) {
        val owner = lifecycleOwner ?: return
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                if (hasUsbCapture) {
                    // USB capture active: main background is USB video.
                    // Camera is only bound if PiP is active.
                    if (cameraLayout != CameraLayout.OFF) {
                        val selector = if (mainFront) {
                            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        }
                        val preview = Preview.Builder().build()
                        preview.setSurfaceProvider(secondaryPreviewView.surfaceProvider)
                        cameraProvider.bindToLifecycle(owner, selector, preview)
                    }
                } else {
                    // No USB capture: Camera is ALWAYS full-screen main.
                    val primarySelector = if (mainFront) {
                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    val primaryPreview = Preview.Builder().build()
                    primaryPreview.setSurfaceProvider(primaryPreviewView.surfaceProvider)

                    if (cameraLayout == CameraLayout.OFF || cameraLayout == CameraLayout.FULL_SCREEN) {
                        // Single camera: only primary full screen
                        cameraProvider.bindToLifecycle(owner, primarySelector, primaryPreview)
                    } else {
                        // PiP requested without USB: PiP must be the OPPOSITE camera!
                        val secondaryFront = !mainFront
                        val secondarySelector = if (secondaryFront) {
                            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        } else {
                            if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                        }

                        val hasConcurrent = checkConcurrentSupport(cameraProvider, primarySelector, secondarySelector)
                        isConcurrentSupported = hasConcurrent

                        if (hasConcurrent) {
                            val secondaryPreview = Preview.Builder().build()
                            secondaryPreview.setSurfaceProvider(secondaryPreviewView.surfaceProvider)

                            val primaryConfig = ConcurrentCamera.SingleCameraConfig(
                                primarySelector,
                                UseCaseGroup.Builder().addUseCase(primaryPreview).build(),
                                owner
                            )
                            val secondaryConfig = ConcurrentCamera.SingleCameraConfig(
                                secondarySelector,
                                UseCaseGroup.Builder().addUseCase(secondaryPreview).build(),
                                owner
                            )
                            cameraProvider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))
                        } else {
                            // If hardware cannot run concurrent cameras, bind primary live
                            cameraProvider.bindToLifecycle(owner, primarySelector, primaryPreview)
                        }
                    }
                }
                cameraFailed = false
            } catch (e: Throwable) {
                Log.e("StudioCameraState", "Failed to bind camera: ${e.message}", e)
                cameraFailed = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun checkConcurrentSupport(
        provider: ProcessCameraProvider,
        primary: CameraSelector,
        secondary: CameraSelector
    ): Boolean {
        return try {
            val concurrentList = provider.availableConcurrentCameraInfos
            for (combination in concurrentList) {
                val hasPrimary = primary.filter(combination).isNotEmpty()
                val hasSecondary = secondary.filter(combination).isNotEmpty()
                if (hasPrimary && hasSecondary) return true
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun release() {
        try {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        } catch (_: Throwable) {
        }
    }
}

@Composable
fun rememberStudioCameraState(
    hasUsbCapture: Boolean,
    mainFront: Boolean,
    cameraLayout: CameraLayout
): StudioCameraState {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = activity as? LifecycleOwner

    val state = remember(context, lifecycleOwner) {
        StudioCameraState(context, lifecycleOwner)
    }

    LaunchedEffect(hasUsbCapture, mainFront, cameraLayout, lifecycleOwner) {
        state.updateBinding(hasUsbCapture, mainFront, cameraLayout)
    }

    DisposableEffect(state) {
        onDispose {
            state.release()
        }
    }

    return state
}

/**
 * Fallback card shown in PiP when the hardware device does not support concurrent dual cameras.
 */
@Composable
fun DualCameraUnavailablePip(
    oppositeCameraFront: Boolean,
    onFlipCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF161B2C))
            .clipToBounds()
            .testTag("dual_camera_unavailable_box"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (oppositeCameraFront) "Front Lens" else "Back Lens",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Dual stream requires hardware concurrent cameras",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onFlipCamera,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                modifier = Modifier.height(26.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "Switch to ${if (oppositeCameraFront) "Front" else "Back"}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
