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
        assertEquals(CameraLayout.FULL_SCREEN, CameraLayout.SPLIT_CAMERA_BOTTOM.next())
        assertEquals(CameraLayout.OFF, CameraLayout.FULL_SCREEN.next())
    }

    @Test
    fun verifyShortcutConfig() {
        val enabledPages = com.example.net.ShortcutConfig.getEnabledPages()
        assertEquals(3, enabledPages.size)
        assertEquals("STREAM_REC", enabledPages[0].id)
        assertEquals("PNG_OVERLAY", enabledPages[1].id)
        assertEquals("CAMERA", enabledPages[2].id)

        // Page A should have 6 buttons: 4 soon + 2 working
        val pageAButtons = enabledPages[0].buttons
        assertEquals(6, pageAButtons.size)
        assertTrue(pageAButtons[0].isSoon)
        assertTrue(pageAButtons[1].isSoon)
        assertTrue(pageAButtons[2].isSoon)
        assertTrue(pageAButtons[3].isSoon)
        assertFalse(pageAButtons[4].isSoon)
        assertEquals(com.example.net.ShortcutConfig.ACTION_MUTE_MIC, pageAButtons[4].id)
        assertEquals(com.example.net.ShortcutConfig.ACTION_LISTEN_MONITOR, pageAButtons[5].id)
    }

    @Test
    fun verifyLegalTerms() {
        assertEquals(1, com.example.ui.LEGAL_TERMS_VERSION)
        assertTrue(com.example.ui.LEGAL_TERMS_TEXT.contains("END USER LICENCE AGREEMENT"))
        assertTrue(com.example.ui.LEGAL_TERMS_TEXT.contains("EXCEED THE AMOUNT ACTUALLY PAID BY YOU FOR THE APP"))
        // Verify R100 was removed as requested
        assertFalse(com.example.ui.LEGAL_TERMS_TEXT.contains("R100"))
    }
}
