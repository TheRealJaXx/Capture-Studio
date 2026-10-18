package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Draggable / pinch-resizable PNG overlay (transparency preserved) drawn over the video.
 * Position is the image centre as a fraction of the screen; size is a fraction of screen width.
 */
@Composable
fun PngOverlay(
    uri: String?,
    visible: Boolean,
    centerX: Float,
    centerY: Float,
    widthFraction: Float,
    onTransformChanged: (cx: Float, cy: Float, width: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var cx by remember { mutableFloatStateOf(centerX) }
    var cy by remember { mutableFloatStateOf(centerY) }
    var wFrac by remember { mutableFloatStateOf(widthFraction) }

    LaunchedEffect(uri) {
        bitmap = if (uri == null) null else withContext(Dispatchers.IO) { loadOverlayBitmap(context, uri) }
    }

    // Save the position/size shortly after the user stops moving it
    LaunchedEffect(cx, cy, wFrac) {
        delay(600)
        onTransformChanged(cx, cy, wFrac)
    }

    val bmp = bitmap
    if (!visible || bmp == null) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        val aspect = bmp.height.toFloat() / bmp.width.toFloat()
        val wPx = wFrac * containerW
        val hPx = wPx * aspect

        Image(
            bitmap = bmp,
            contentDescription = "Overlay",
            modifier = Modifier
                .offset { IntOffset((cx * containerW - wPx / 2f).roundToInt(), (cy * containerH - hPx / 2f).roundToInt()) }
                .size(with(density) { wPx.toDp() }, with(density) { hPx.toDp() })
                .pointerInput(containerW, containerH) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        wFrac = (wFrac * zoom).coerceIn(0.05f, 2f)
                        cx = (cx + pan.x / containerW).coerceIn(0f, 1f)
                        cy = (cy + pan.y / containerH).coerceIn(0f, 1f)
                    }
                }
        )
    }
}

private fun loadOverlayBitmap(context: Context, uriString: String): ImageBitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}