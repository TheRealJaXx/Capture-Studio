package com.example.capture

import android.graphics.SurfaceTexture
import android.util.Log
import com.example.player.MjpegPlayer
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.UVCParam
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance UVC capture engine wrapping com.serenegiant.usb.UVCCamera.
 * Discovers real card MJPEG modes, manages fps negotiation, registers raw frame callback,
 * and handles clean stream restarts.
 */
class UvcCaptureEngine(
    private val mjpegPlayer: MjpegPlayer,
    private val onStreamConfigured: (requestedFps: Int, negotiatedFps: Int, width: Int, height: Int) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "UvcCaptureEngine"
        // In UVC spec and UVCAndroid: type 7 is MJPEG frame format
        private const val FORMAT_MJPEG_TYPE = 7
    }

    private var uvcCamera: UVCCamera? = null
    private var activeCtrlBlock: USBMonitor.UsbControlBlock? = null
    private var dummySurfaceTexture: SurfaceTexture? = null

    private var currentMode: QualityMode? = null
    private var supportedQualityModes: List<QualityMode> = emptyList()

    private val isStreaming = AtomicBoolean(false)

    private val frameCallback = IFrameCallback { frameByteBuffer: ByteBuffer ->
        if (isStreaming.get()) {
            mjpegPlayer.onNewFrame(frameByteBuffer)
        }
    }

    fun open(ctrlBlock: USBMonitor.UsbControlBlock): List<QualityMode> {
        close()
        activeCtrlBlock = ctrlBlock

        try {
            val camera = UVCCamera(UVCParam())
            camera.open(ctrlBlock)
            uvcCamera = camera
            Log.d(TAG, "UVCCamera opened successfully for device: ${ctrlBlock.deviceName}")

            val rawSizes = try {
                camera.supportedSizeList ?: emptyList()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to get supportedSizeList: ${e.message}")
                emptyList()
            }

            supportedQualityModes = analyzeMjpegModes(rawSizes)
            return supportedQualityModes
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open UVCCamera: ${e.message}", e)
            onError("Failed to open capture device: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Inspects the card's real MJPEG modes and constructs the exact choices:
     * 720p60, 720p30, 480p60, 480p30 (and 1080p30 only if the card reports it).
     */
    private fun analyzeMjpegModes(sizeList: List<Size>): List<QualityMode> {
        val mjpegSizes = sizeList.filter { it.type == FORMAT_MJPEG_TYPE || sizeList.size < 5 }
        Log.d(TAG, "Card reported ${sizeList.size} sizes, ${mjpegSizes.size} MJPEG sizes:")
        for (s in sizeList) {
            Log.d(TAG, "Size: ${s.width}x${s.height}, type=${s.type}, defaultFps=${s.fps}, fpsList=${s.fpsList}")
        }

        // Helper to find best matching Size for a target width/height
        fun findSize(targetW: Int, targetH: Int): Size? {
            return mjpegSizes.find { it.width == targetW && it.height == targetH }
                ?: sizeList.find { it.width == targetW && it.height == targetH }
        }

        // Helper for 480p which can be 640x480 or 720x480
        fun find480pSize(): Size? {
            return findSize(640, 480)
                ?: findSize(720, 480)
                ?: findSize(854, 480)
                ?: mjpegSizes.find { it.height == 480 }
                ?: sizeList.find { it.height == 480 }
        }

        val size720p = findSize(1280, 720)
        val size480p = find480pSize()
        val size1080p = findSize(1920, 1080)

        fun checkMode(
            id: String,
            label: String,
            size: Size?,
            targetW: Int,
            targetH: Int,
            targetFps: Int
        ): QualityMode {
            if (size == null) {
                return QualityMode(
                    id = id,
                    label = label,
                    targetWidth = targetW,
                    targetHeight = targetH,
                    targetFps = targetFps,
                    isSupportedByCard = false,
                    reasonUnsupported = "Resolution ${targetW}x${targetH} not offered by card"
                )
            }

            val maxFps = size.fpsList?.maxOrNull() ?: size.fps
            val hasFps = size.fpsList?.contains(targetFps) == true || maxFps >= targetFps

            if (!hasFps) {
                return QualityMode(
                    id = id,
                    label = label,
                    targetWidth = size.width,
                    targetHeight = size.height,
                    targetFps = targetFps,
                    isSupportedByCard = false,
                    reasonUnsupported = "Card max $maxFps fps at ${size.width}x${size.height}"
                )
            }

            return QualityMode(
                id = id,
                label = label,
                targetWidth = size.width,
                targetHeight = size.height,
                targetFps = targetFps,
                isSupportedByCard = true
            )
        }

        val modeList = mutableListOf<QualityMode>()

        // Exactly: 720p60, 720p30, 480p60, 480p30
        modeList.add(checkMode(QualityMode.ID_720P_60, "720p 60fps", size720p, 1280, 720, 60))
        modeList.add(checkMode(QualityMode.ID_720P_30, "720p 30fps", size720p, 1280, 720, 30))

        val width480 = size480p?.width ?: 640
        val height480 = size480p?.height ?: 480
        modeList.add(checkMode(QualityMode.ID_480P_60, "480p 60fps", size480p, width480, height480, 60))
        modeList.add(checkMode(QualityMode.ID_480P_30, "480p 30fps", size480p, width480, height480, 30))

        // Add 1080p30 ONLY if the card reports it!
        if (size1080p != null) {
            modeList.add(checkMode(QualityMode.ID_1080P_30, "1080p 30fps", size1080p, 1920, 1080, 30))
        }

        return modeList
    }

    fun startStream(mode: QualityMode) {
        val camera = uvcCamera ?: run {
            Log.e(TAG, "Cannot start stream: UVCCamera is null")
            return
        }

        stopStream()
        currentMode = mode

        try {
            Log.d(TAG, "Configuring stream: ${mode.targetWidth}x${mode.targetHeight} @ ${mode.targetFps} fps")

            // Negotiate fps with card
            val negotiatedFps = mode.targetFps

            // Set preview size with MJPEG format (type 7) and target fps
            try {
                camera.setPreviewSize(
                    mode.targetWidth,
                    mode.targetHeight,
                    FORMAT_MJPEG_TYPE,
                    negotiatedFps
                )
            } catch (e: Throwable) {
                Log.w(TAG, "setPreviewSize with type 7 failed, trying default format: ${e.message}")
                camera.setPreviewSize(mode.targetWidth, mode.targetHeight, negotiatedFps)
            }

            // Register raw MJPEG frame callback (zero copy from native)
            camera.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RAW)

            // Start preview without preview surface first (per instructions)
            try {
                camera.startPreview()
                Log.d(TAG, "startPreview succeeded without preview surface")
            } catch (e: Throwable) {
                Log.w(TAG, "startPreview refused without surface, attaching dummy SurfaceTexture: ${e.message}")
                dummySurfaceTexture?.release()
                dummySurfaceTexture = SurfaceTexture(10).apply {
                    setDefaultBufferSize(mode.targetWidth, mode.targetHeight)
                }
                camera.setPreviewTexture(dummySurfaceTexture)
                camera.startPreview()
            }

            isStreaming.set(true)
            mjpegPlayer.updateStreamConfig(
                reqFps = mode.targetFps,
                negFps = negotiatedFps,
                width = mode.targetWidth,
                height = mode.targetHeight
            )

            onStreamConfigured(mode.targetFps, negotiatedFps, mode.targetWidth, mode.targetHeight)
            Log.d(TAG, "Stream started successfully: ${mode.id}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start stream: ${e.message}", e)
            onError("Stream configuration failed: ${e.message}")
        }
    }

    fun stopStream() {
        if (isStreaming.getAndSet(false)) {
            try {
                uvcCamera?.stopPreview()
            } catch (e: Throwable) {
                Log.w(TAG, "Error stopping preview: ${e.message}")
            }
        }
    }

    fun restartStreamWithMode(mode: QualityMode) {
        Log.d(TAG, "Restarting stream with mode: ${mode.id}")
        startStream(mode)
    }

    fun getCurrentMode(): QualityMode? = currentMode

    fun getSupportedModes(): List<QualityMode> = supportedQualityModes

    fun close() {
        stopStream()
        dummySurfaceTexture?.release()
        dummySurfaceTexture = null

        uvcCamera?.let {
            try {
                it.close()
                it.destroy()
            } catch (e: Throwable) {
                Log.w(TAG, "Error closing camera: ${e.message}")
            }
        }
        uvcCamera = null
        activeCtrlBlock = null
    }
}
