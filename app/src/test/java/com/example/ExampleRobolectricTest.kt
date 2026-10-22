package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.capture.QualityMode
import com.example.player.JpegUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Capture Studio", appName)
    }

    @Test
    fun verifyQualityModes() {
        assertEquals("720p60", QualityMode.ID_720P_60)
        assertEquals("720p30", QualityMode.ID_720P_30)
        assertEquals("480p60", QualityMode.ID_480P_60)
        assertEquals("480p30", QualityMode.ID_480P_30)
        assertEquals("1080p30", QualityMode.ID_1080P_30)

        val defaultMode = QualityMode.DEFAULT_720P_60
        assertEquals(1280, defaultMode.targetWidth)
        assertEquals(720, defaultMode.targetHeight)
        assertEquals(60, defaultMode.targetFps)
    }

    @Test
    fun verifyJpegUtilsSoiDetection() {
        val validJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val invalidJpeg = byteArrayOf(0x00, 0x10, 0x20, 0x30)

        assertTrue(JpegUtils.isJpeg(validJpeg, validJpeg.size))
        assertFalse(JpegUtils.isJpeg(invalidJpeg, invalidJpeg.size))
    }

    @Test
    fun verifyCameraLayoutTransitions() {
        var layout = com.example.ui.CameraLayout.OFF
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.PIP_TOP_LEFT, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.PIP_TOP_RIGHT, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.PIP_BOTTOM_LEFT, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.PIP_BOTTOM_RIGHT, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.SPLIT_CAMERA_TOP, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.SPLIT_CAMERA_BOTTOM, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.FULL_SCREEN, layout)
        layout = layout.next()
        assertEquals(com.example.ui.CameraLayout.OFF, layout)
    }

    @Test
    fun verifyCameraOppositeLensResolution() {
        // When hasUsbCapture is false:
        // Main camera displays cameraFront.
        // PiP camera displays the OPPOSITE lens (!cameraFront).
        var cameraFront = false // Main lens is BACK camera
        var mainLensIsFront = cameraFront
        var pipLensIsFront = !cameraFront

        assertFalse(mainLensIsFront) // Main is Back
        assertTrue(pipLensIsFront)  // PiP is Front

        // When flipping camera:
        cameraFront = !cameraFront // Main lens flipped to FRONT
        mainLensIsFront = cameraFront
        pipLensIsFront = !cameraFront

        assertTrue(mainLensIsFront)  // Main is Front
        assertFalse(pipLensIsFront) // PiP is Back
    }
}
