package com.example.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Build
import android.os.Process
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class PlayerStats(
    val captureFps: Float = 0f,
    val negotiatedFps: Int = 60,
    val requestedFps: Int = 60,
    val decodeTimeMs: Long = 0,
    val frameAgeMs: Long = 0,
    val droppedFrames: Long = 0
)

enum class ScaleMode {
    FIT,
    FILL
}

/**
 * Ultra-low latency MJPEG player decoding directly to a SurfaceView's Surface.
 * Adheres to latency rules:
 * - Dedicated high-priority decoding thread
 * - Zero per-frame heap allocations in the capture/decode/render path
 * - Latest-frame-wins with single-slot handoff (no queue buffering)
 * - On API 30+, calls surface.setFrameRate with FIXED_SOURCE
 */
class MjpegPlayer(
    private val onStatsUpdated: (PlayerStats) -> Unit
) {
    companion object {
        private const val TAG = "MjpegPlayer"
        private const val MAX_FRAME_BUFFER_SIZE = 4 * 1024 * 1024 // 4 MB for high-res MJPEG frames
    }

    private class FrameBuffer(val capacity: Int) {
        val data = ByteArray(capacity)
        var length = 0
        var timestampMs = 0L
    }

    // Pre-allocated reusable double buffer for zero-allocation handoff
    private val bufferA = FrameBuffer(MAX_FRAME_BUFFER_SIZE)
    private val bufferB = FrameBuffer(MAX_FRAME_BUFFER_SIZE)
    private var writeBuffer = bufferA

    // Pre-allocated DHT injection buffer if needed
    private val dhtBuffer = ByteArray(MAX_FRAME_BUFFER_SIZE + 1024)

    // Single-slot handoff for latest-frame-wins
    private val latestFrame = AtomicReference<FrameBuffer?>(null)
    private val droppedFramesCounter = AtomicLong(0)

    // Thread synchronization
    private val lock = ReentrantLock()
    private val frameCondition = lock.newCondition()
    private val isRunning = AtomicBoolean(false)
    private var decodeThread: Thread? = null

    // Surface reference
    private var activeSurfaceHolder: SurfaceHolder? = null
    private var activeSurface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    // Render parameters & reuse
    private var frameWidth = 1280
    private var frameHeight = 720
    private var targetFps = 60
    private var requestedFps = 60
    private var negotiatedFps = 60
    private var scaleMode = ScaleMode.FIT

    private var reusableBitmap: Bitmap? = null
    private val bitmapOptions = BitmapFactory.Options().apply {
        inMutable = true
        inSampleSize = 1
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    private val srcRect = Rect()
    private val dstRect = Rect()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    // Performance metrics
    private var frameCountInSecond = 0
    private var lastFpsTimestamp = 0L
    private var measuredCaptureFps = 0f
    private var lastDecodeDuration = 0L
    private var lastFrameAge = 0L
    private var lastStatsPushTimestamp = 0L

    init {
        updateReusableBitmap(frameWidth, frameHeight)
    }

    fun setScaleMode(mode: ScaleMode) {
        scaleMode = mode
        updateDestRect()
    }

    fun getScaleMode(): ScaleMode = scaleMode

    fun updateStreamConfig(reqFps: Int, negFps: Int, width: Int, height: Int) {
        requestedFps = reqFps
        negotiatedFps = negFps
        targetFps = if (negFps > 0) negFps else reqFps

        if (frameWidth != width || frameHeight != height || reusableBitmap == null) {
            frameWidth = width
            frameHeight = height
            updateReusableBitmap(width, height)
        }

        applyFrameRateToSurface()
    }

    private fun updateReusableBitmap(width: Int, height: Int) {
        try {
            reusableBitmap?.recycle()
            reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmapOptions.inBitmap = reusableBitmap
            srcRect.set(0, 0, width, height)
            updateDestRect()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to create reusable bitmap: ${e.message}")
        }
    }

    fun onSurfaceCreated(holder: SurfaceHolder) {
        activeSurfaceHolder = holder
        activeSurface = holder.surface
        applyFrameRateToSurface()
    }

    fun onSurfaceChanged(holder: SurfaceHolder, width: Int, height: Int) {
        activeSurfaceHolder = holder
        activeSurface = holder.surface
        surfaceWidth = width
        surfaceHeight = height
        updateDestRect()
        applyFrameRateToSurface()
    }

    fun onSurfaceDestroyed() {
        activeSurfaceHolder = null
        activeSurface = null
    }

    private fun applyFrameRateToSurface() {
        val surface = activeSurface ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface.isValid) {
            try {
                surface.setFrameRate(
                    targetFps.toFloat(),
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE
                )
                Log.d(TAG, "Set surface frame rate to $targetFps fps")
            } catch (e: Throwable) {
                Log.w(TAG, "surface.setFrameRate failed: ${e.message}")
            }
        }
    }

    private fun updateDestRect() {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || frameWidth <= 0 || frameHeight <= 0) return

        val viewAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val frameAspect = frameWidth.toFloat() / frameHeight.toFloat()

        when (scaleMode) {
            ScaleMode.FIT -> {
                if (viewAspect > frameAspect) {
                    // Pillarbox (black bars left and right)
                    val targetW = (surfaceHeight * frameAspect).toInt()
                    val left = (surfaceWidth - targetW) / 2
                    dstRect.set(left, 0, left + targetW, surfaceHeight)
                } else {
                    // Letterbox (black bars top and bottom)
                    val targetH = (surfaceWidth / frameAspect).toInt()
                    val top = (surfaceHeight - targetH) / 2
                    dstRect.set(0, top, surfaceWidth, top + targetH)
                }
            }
            ScaleMode.FILL -> {
                if (viewAspect > frameAspect) {
                    // Crop top and bottom to fill width
                    val targetH = (surfaceWidth / frameAspect).toInt()
                    val top = (surfaceHeight - targetH) / 2
                    dstRect.set(0, top, surfaceWidth, top + targetH)
                } else {
                    // Crop left and right to fill height
                    val targetW = (surfaceHeight * frameAspect).toInt()
                    val left = (surfaceWidth - targetW) / 2
                    dstRect.set(left, 0, left + targetW, surfaceHeight)
                }
            }
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        decodeThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            decodeLoop()
        }, "MjpegDecodeThread").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stop() {
        isRunning.set(false)
        lock.withLock {
            frameCondition.signalAll()
        }
        try {
            decodeThread?.join(500)
        } catch (_: InterruptedException) {
        }
        decodeThread = null
    }

    /**
     * Called directly from UVC onFrame callback with incoming MJPEG frame.
     * ZERO per-frame heap allocations.
     */
    fun onNewFrame(byteBuffer: ByteBuffer) {
        val length = byteBuffer.remaining()
        if (length <= 0 || length > MAX_FRAME_BUFFER_SIZE) return

        val targetBuf = writeBuffer
        byteBuffer.get(targetBuf.data, 0, length)
        targetBuf.length = length
        targetBuf.timestampMs = System.currentTimeMillis()

        // Flip write buffer to other pre-allocated buffer
        writeBuffer = if (writeBuffer === bufferA) bufferB else bufferA

        // Latest frame handoff - drop any frame that was still waiting in queue
        val prev = latestFrame.getAndSet(targetBuf)
        if (prev != null) {
            droppedFramesCounter.incrementAndGet()
        }

        // Wake up decode thread immediately
        lock.withLock {
            frameCondition.signal()
        }
    }

    private fun decodeLoop() {
        lastFpsTimestamp = System.currentTimeMillis()

        while (isRunning.get()) {
            var frame: FrameBuffer? = null

            lock.withLock {
                frame = latestFrame.getAndSet(null)
                if (frame == null && isRunning.get()) {
                    try {
                        frameCondition.await()
                    } catch (_: InterruptedException) {
                        return
                    }
                    frame = latestFrame.getAndSet(null)
                }
            }

            if (frame != null && isRunning.get()) {
                processAndRenderFrame(frame!!)
            }
        }
    }

    private fun processAndRenderFrame(frame: FrameBuffer) {
        val now = System.currentTimeMillis()
        val frameTimestamp = frame.timestampMs
        val rawData = frame.data
        val rawLen = frame.length

        // FPS tracking
        frameCountInSecond++
        if (now - lastFpsTimestamp >= 1000) {
            val elapsed = (now - lastFpsTimestamp) / 1000f
            measuredCaptureFps = frameCountInSecond / elapsed
            frameCountInSecond = 0
            lastFpsTimestamp = now
        }

        // Validate JPEG SOI
        if (!JpegUtils.isJpeg(rawData, rawLen)) {
            return
        }

        // Inject DHT if missing to prevent decode failure on cheap capture cards
        val decodeData: ByteArray
        val decodeLen: Int
        if (!JpegUtils.hasHuffmanTable(rawData, rawLen)) {
            decodeLen = JpegUtils.injectHuffmanTable(rawData, rawLen, dhtBuffer)
            decodeData = dhtBuffer
        } else {
            decodeData = rawData
            decodeLen = rawLen
        }

        // Zero-allocation decoding using inBitmap
        val decodeStart = System.currentTimeMillis()
        val decoded = try {
            BitmapFactory.decodeByteArray(decodeData, 0, decodeLen, bitmapOptions)
        } catch (e: Throwable) {
            Log.e(TAG, "BitmapFactory decode failed: ${e.message}")
            null
        }

        val decodeEnd = System.currentTimeMillis()
        lastDecodeDuration = decodeEnd - decodeStart
        lastFrameAge = decodeEnd - frameTimestamp

        val surface = activeSurface
        if (decoded != null && surface != null && surface.isValid) {
            renderToSurface(decoded, surface)
        }

        // Push stats update periodically (every 250ms)
        if (now - lastStatsPushTimestamp >= 250) {
            lastStatsPushTimestamp = now
            onStatsUpdated(
                PlayerStats(
                    captureFps = measuredCaptureFps,
                    negotiatedFps = negotiatedFps,
                    requestedFps = requestedFps,
                    decodeTimeMs = lastDecodeDuration,
                    frameAgeMs = lastFrameAge,
                    droppedFrames = droppedFramesCounter.get()
                )
            )
        }
    }

    private fun renderToSurface(bitmap: Bitmap, surface: Surface) {
        val holder = activeSurfaceHolder ?: return
        var canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                surface.lockHardwareCanvas()
            } catch (e: Throwable) {
                try {
                    holder.lockCanvas()
                } catch (_: Throwable) {
                    null
                }
            }
        } else {
            try {
                holder.lockCanvas()
            } catch (_: Throwable) {
                null
            }
        }

        if (canvas != null) {
            try {
                // Clear letterbox / background
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.SRC)
                canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
            } finally {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (e: Throwable) {
                    Log.w(TAG, "unlockCanvasAndPost failed: ${e.message}")
                }
            }
        }
    }

    fun release() {
        stop()
        reusableBitmap?.recycle()
        reusableBitmap = null
    }
}
