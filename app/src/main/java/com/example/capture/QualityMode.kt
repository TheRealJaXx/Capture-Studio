package com.example.capture

data class QualityMode(
    val id: String,
    val label: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val targetFps: Int,
    val isSupportedByCard: Boolean = true,
    val reasonUnsupported: String? = null
) {
    companion object {
        const val ID_720P_60 = "720p60"
        const val ID_720P_30 = "720p30"
        const val ID_480P_60 = "480p60"
        const val ID_480P_30 = "480p30"
        const val ID_1080P_30 = "1080p30"

        val DEFAULT_720P_60 = QualityMode(
            id = ID_720P_60,
            label = "720p 60fps",
            targetWidth = 1280,
            targetHeight = 720,
            targetFps = 60
        )

        val DEFAULT_720P_30 = QualityMode(
            id = ID_720P_30,
            label = "720p 30fps",
            targetWidth = 1280,
            targetHeight = 720,
            targetFps = 30
        )

        val DEFAULT_480P_60 = QualityMode(
            id = ID_480P_60,
            label = "480p 60fps",
            targetWidth = 640,
            targetHeight = 480,
            targetFps = 60
        )

        val DEFAULT_480P_30 = QualityMode(
            id = ID_480P_30,
            label = "480p 30fps",
            targetWidth = 640,
            targetHeight = 480,
            targetFps = 30
        )

        val DEFAULT_1080P_30 = QualityMode(
            id = ID_1080P_30,
            label = "1080p 30fps",
            targetWidth = 1920,
            targetHeight = 1080,
            targetFps = 30
        )
    }
}
