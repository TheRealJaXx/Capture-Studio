package com.example
 
import com.example.ui.CameraAspectRatio
import com.example.ui.CameraLayout
import com.example.ui.CameraPipSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun verifyCameraPipSizes() {
        assertEquals(0.18f, CameraPipSize.SMALL.widthFraction, 0.001f)
        assertEquals(0.24f, CameraPipSize.MEDIUM.widthFraction, 0.001f)
        assertEquals(0.30f, CameraPipSize.LARGE.widthFraction, 0.001f)

        assertEquals(CameraPipSize.MEDIUM, CameraPipSize.SMALL.next())
        assertEquals(CameraPipSize.LARGE, CameraPipSize.MEDIUM.next())
        assertEquals(CameraPipSize.SMALL, CameraPipSize.LARGE.next())
    }

    @Test
    fun verifyCameraAspectRatios() {
        assertEquals(16f / 9f, CameraAspectRatio.RATIO_16_9.ratio, 0.001f)
        assertEquals(4f / 3f, CameraAspectRatio.RATIO_4_3.ratio, 0.001f)
        assertEquals(1f, CameraAspectRatio.RATIO_1_1.ratio, 0.001f)
        assertEquals(9f / 16f, CameraAspectRatio.RATIO_9_16.ratio, 0.001f)

        assertEquals(CameraAspectRatio.RATIO_4_3, CameraAspectRatio.RATIO_16_9.next())
        assertEquals(CameraAspectRatio.RATIO_1_1, CameraAspectRatio.RATIO_4_3.next())
        assertEquals(CameraAspectRatio.RATIO_9_16, CameraAspectRatio.RATIO_1_1.next())
        assertEquals(CameraAspectRatio.RATIO_16_9, CameraAspectRatio.RATIO_9_16.next())
    }

    @Test
    fun verifyCameraLayout() {
        assertFalse(CameraLayout.OFF.isSplit)
        assertFalse(CameraLayout.PIP_TOP_LEFT.isSplit)
        assertTrue(CameraLayout.SPLIT_CAMERA_TOP.isSplit)
        assertTrue(CameraLayout.SPLIT_CAMERA_BOTTOM.isSplit)

        assertEquals(CameraLayout.PIP_TOP_LEFT, CameraLayout.OFF.next())
        assertEquals(CameraLayout.OFF, CameraLayout.SPLIT_CAMERA_BOTTOM.next())
    }
}
