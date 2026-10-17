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
}
