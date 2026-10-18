package com.example.capture

import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
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
 * Specifically optimized for HDMI-to-USB capture cards (e.g. MacroSilicon MS2109,
 * Cam Link, USB 2.0/3.0 capture dongles).
 *
 * Implements latency rules:
 * - Direct frame callback path to MjpegPlayer (zero extra buffering)
 * - Bandwidth fix quirk (UVC_QUIRK_FIX_BANDWIDTH) for stable USB endpoint negotiation
 * - Real card MJPEG mode discovery and exact fps negotiation
 * - Never locks or hijacks the SurfaceView so MjpegPlayer has exclusive, unhindered rendering
 * - Clean stream restarts without restarting the app
 */
class UvcCaptureEngine(
    private val mjpegPlayer: MjpegPlayer,
    private val onStreamConfigured: (requestedFps: Int, negotiatedFps: Int, width: Int, height: Int) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "UvcCaptureEngine"

        // UVC Descriptor Format Subtypes:
        const val UVC_VS_FRAME_UNCOMPRESSED = 2
        const val UVC_VS_FORMAT_MJPEG = 6
        const val UVC_VS_FRAME_MJPEG = 7
        const val FRAME_FORMAT_MJPEG = 1
    }

    private var uvcCamera: UVCCamera? = null
    private var activeCtrlBlock: USBMonitor.UsbControlBlock? = null

    private var currentMode: QualityMode? = null
    private var supportedQualityModes: List<QualityMode> = emptyList()
    private var cardSizeList: List<Size> = emptyList()

    private val isStreaming = AtomicBoolean(false)

    // Optional offscreen drain reader if a camera specifically requires a preview target to pump frames
    private var offscreenDrainThread: HandlerThread? = null
    private var offscreenDrainHandler: Handler? = null
    private var offscreenImageReader: ImageReader? = null

    private val frameCallback = IFrameCallback { frameByteBuffer: ByteBuffer ->
        if (isStreaming.get()) {
            mjpegPlayer.onNewFrame(frameByteBuffer)
        }
    }

    fun open(ctrlBlock: USBMonitor.UsbControlBlock): List<QualityMode> {
        close()
        activeCtrlBlock = ctrlBlock

        try {
            // Apply bandwidth quirk: essential for USB capture cards (e.g. MS2109) on Android
            val quirks = UVCCamera.UVC_QUIRK_FIX_BANDWIDTH or UVCCamera.getRecommendedPlatformQuirks()
            val param = UVCParam().apply {
                setQuirks(quirks)
            }

            val camera = UVCCamera(param)
            val openResult = camera.open(ctrlBlock)
            Log.i(TAG, "UVCCamera.open result: $openResult for device: ${ctrlBlock.deviceName}")

            if (openResult != 0) {
                Log.e(TAG, "UVCCamera.open returned non-zero error: $openResult")
                onError("Failed to open capture device (error code $openResult). Please re-plug.")
                return emptyList()
            }

            uvcCamera = camera

            val rawSizes = try {
                camera.supportedSizeList ?: emptyList()
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to get supportedSizeList: ${e.message}")
                emptyList()
            }

            cardSizeList = rawSizes
            supportedQualityModes = analyzeMjpegModes(rawSizes)
            return supportedQualityModes
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to open UVCCamera: ${e.message}", e)
            onError("Failed to open capture device: ${e.message}")
            return emptyList()
        }
    }

    /**
     * Inspects the card's real modes and constructs the exact choices:
     * 720p60, 720p30, 480p60, 480p30 (and 1080p30 only if the card reports it).
     */
    private fun analyzeMjpegModes(sizeList: List<Size>): List<QualityMode> {
        val mjpegSizes = sizeList.filter {
            it.type == UVC_VS_FRAME_MJPEG || it.type == UVC_VS_FORMAT_MJPEG || it.type == FRAME_FORMAT_MJPEG
        }.ifEmpty { sizeList }

        Log.i(TAG, "Card reported ${sizeList.size} sizes total (${mjpegSizes.size} MJPEG candidate sizes):")
        for (s in sizeList) {
            Log.d(TAG, "  Size: ${s.width}x${s.height}, type=${s.type}, defaultFps=${s.fps}, fpsList=${s.fpsList}")
        }

        fun findSize(targetW: Int, targetH: Int): Size? {
            return mjpegSizes.find { it.width == targetW && it.height == targetH }
                ?: sizeList.find { it.width == targetW && it.height == targetH }
        }

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
                    reasonUnsupported = "Resolution ${targetW}x${targetH} not offered by card",
                    cardRawSize = null
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
                    reasonUnsupported = "Card max $maxFps fps at ${size.width}x${size.height}",
                    cardRawSize = size
                )
            }

            return QualityMode(
                id = id,
                label = label,
                targetWidth = size.width,
                targetHeight = size.height,
                targetFps = targetFps,
                isSupportedByCard = true,
                cardRawSize = size
            )
        }

        val modeList = mutableListOf<QualityMode>()

        // Choices: 720p60, 720p30, 480p60, 480p30
        modeList.add(checkMode(QualityMode.ID_720P_60, "720p 60fps", size720p, 1280, 720, 60))
        modeList.add(checkMode(QualityMode.ID_720P_30, "720p 30fps", size720p, 1280, 720, 30))

        val width480 = size480p?.width ?: 640
        val height480 = size480p?.height ?: 480
        modeList.add(checkMode(QualityMode.ID_480P_60, "480p 60fps", size480p, width480, height480, 60))
        modeList.add(checkMode(QualityMode.ID_480P_30, "480p 30fps", size480p, width480, height480, 30))

        // Add 1080p30 ONLY if the card reports it
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
            Log.i(TAG, "Configuring stream: ${mode.targetWidth}x${mode.targetHeight} @ ${mode.targetFps} fps")

            // Determine exact type and negotiated fps that the card accepts
            val targetType = mode.cardRawSize?.type ?: UVC_VS_FRAME_MJPEG
            val negotiatedFps = when {
                mode.cardRawSize?.fpsList?.contains(mode.targetFps) == true -> mode.targetFps
                !mode.cardRawSize?.fpsList.isNullOrEmpty() -> {
                    mode.cardRawSize!!.fpsList.filter { it <= mode.targetFps }.maxOrNull()
                        ?: mode.cardRawSize!!.fpsList.first()
                }
                mode.cardRawSize?.fps != null && mode.cardRawSize!!.fps > 0 -> mode.cardRawSize!!.fps
                else -> mode.targetFps
            }

            Log.i(TAG, "Negotiated fps: $negotiatedFps (requested ${mode.targetFps}), type=$targetType")

            // Construct exact matching Size object to satisfy checkSizeValid() in UVCCamera
            val exactSize = Size(
                targetType,
                mode.targetWidth,
                mode.targetHeight,
                negotiatedFps,
                mode.cardRawSize?.fpsList ?: listOf(negotiatedFps)
            )

            try {
                camera.setPreviewSize(exactSize)
                Log.i(TAG, "camera.setPreviewSize(exactSize) succeeded")
            } catch (e: Throwable) {
                Log.w(TAG, "setPreviewSize with Size failed: ${e.message}, trying 4-arg")
                try {
                    camera.setPreviewSize(mode.targetWidth, mode.targetHeight, targetType, negotiatedFps)
                    Log.i(TAG, "camera.setPreviewSize 4-arg succeeded")
                } catch (e2: Throwable) {
                    Log.e(TAG, "setPreviewSize 4-arg also failed: ${e2.message}, using fallback size")
                    val fallback = camera.supportedSizeOne
                    if (fallback != null) {
                        camera.setPreviewSize(fallback)
                    }
                }
            }

            // Register frame callback: delivers raw MJPEG frames directly to MjpegPlayer
            camera.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RAW)

            // Start preview streaming
            try {
                camera.startPreview()
                isStreaming.set(true)
                Log.i(TAG, "UVCCamera preview started (direct frame callback mode)")
            } catch (e: Throwable) {
                Log.w(TAG, "startPreview without surface failed: ${e.message}, applying offscreen drain surface")
                setupOffscreenDrain(camera, mode.targetWidth, mode.targetHeight)
                camera.startPreview()
                isStreaming.set(true)
            }

            mjpegPlayer.updateStreamConfig(
                reqFps = mode.targetFps,
                negFps = negotiatedFps,
                width = mode.targetWidth,
                height = mode.targetHeight
            )

            onStreamConfigured(mode.targetFps, negotiatedFps, mode.targetWidth, mode.targetHeight)
            Log.i(TAG, "Stream active for mode: ${mode.id}")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start stream: ${e.message}", e)
            onError("Stream configuration failed: ${e.message}")
        }
    }

    /**
     * Fallback offscreen surface in case a specific UVC driver requires a surface to stream.
     * Uses ImageReader which consumes and recycles frames immediately without needing an OpenGL context.
     */
    private fun setupOffscreenDrain(camera: UVCCamera, width: Int, height: Int) {
        releaseOffscreenDrain()
        try {
            val thread = HandlerThread("UvcOffscreenDrain").apply { start() }
            val handler = Handler(thread.looper)
            val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
            reader.setOnImageAvailableListener({ r ->
                try {
                    r.acquireLatestImage()?.close()
                } catch (_: Throwable) {
                }
            }, handler)

            camera.setPreviewDisplay(reader.surface)
            offscreenDrainThread = thread
            offscreenDrainHandler = handler
            offscreenImageReader = reader
            Log.i(TAG, "Offscreen ImageReader drain configured (${width}x${height})")
        } catch (e: Throwable) {
            Log.w(TAG, "setupOffscreenDrain failed: ${e.message}")
        }
    }

    private fun releaseOffscreenDrain() {
        offscreenImageReader?.close()
        offscreenImageReader = null
        offscreenDrainThread?.quitSafely()
        offscreenDrainThread = null
        offscreenDrainHandler = null
    }

    fun stopStream() {
        if (isStreaming.getAndSet(false)) {
            try {
                uvcCamera?.stopPreview()
            } catch (e: Throwable) {
                Log.w(TAG, "Error stopping preview: ${e.message}")
            }
        }
        releaseOffscreenDrain()
    }

    fun restartStreamWithMode(mode: QualityMode) {
        Log.i(TAG, "Restarting stream with mode: ${mode.id}")
        startStream(mode)
    }

    fun getCurrentMode(): QualityMode? = currentMode

    fun getSupportedModes(): List<QualityMode> = supportedQualityModes

    fun close() {
        stopStream()
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
