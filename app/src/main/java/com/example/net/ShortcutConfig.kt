package com.example.net

/**
 * Single source of truth for Remote and Feed shortcut definitions.
 * All page orders, button keys, labels, and enabled states are configured here.
 */
object ShortcutConfig {

    // Stable string action IDs
    const val ACTION_START_STREAM = "START_STREAM"
    const val ACTION_END_STREAM = "END_STREAM"
    const val ACTION_START_REC = "START_REC"
    const val ACTION_STOP_REC = "STOP_REC"
    const val ACTION_MUTE_MIC = "MUTE_MIC"
    const val ACTION_LISTEN_MONITOR = "LISTEN_MONITOR"

    const val ACTION_PNG_OVERLAY = "PNG_OVERLAY"
    const val ACTION_FIT_FILL = "FIT_FILL"

    const val ACTION_CAMERA_PIP = "CAMERA_PIP"
    const val ACTION_FLIP_CAMERA = "FLIP_CAMERA"
    const val ACTION_PIP_SIZE = "PIP_SIZE"
    const val ACTION_ASPECT_RATIO = "ASPECT_RATIO"

    data class ShortcutButtonDef(
        val id: String,
        val label: String,
        val glowColorArgb: Long = 0xFF00E5FF, // Default Cyan glow
        val isSoon: Boolean = false,
        val isClickable: Boolean = true
    )

    data class ShortcutPageDef(
        val id: String,
        val title: String,
        val enabled: Boolean,
        val buttons: List<ShortcutButtonDef>
    )

    /**
     * Master definition list of all shortcut pages.
     * Pages marked enabled=false do NOT appear in the pager or indicators,
     * but remain defined so they can be enabled in future releases.
     */
    val ALL_PAGES = listOf(
        // Page A: "STREAM / REC"
        ShortcutPageDef(
            id = "STREAM_REC",
            title = "STREAM / REC",
            enabled = true,
            buttons = listOf(
                ShortcutButtonDef(
                    id = ACTION_START_STREAM,
                    label = "START STREAM",
                    glowColorArgb = 0xFFEF4444, // Red
                    isSoon = true,
                    isClickable = false
                ),
                ShortcutButtonDef(
                    id = ACTION_END_STREAM,
                    label = "END STREAM",
                    glowColorArgb = 0xFFEF4444, // Red
                    isSoon = true,
                    isClickable = false
                ),
                ShortcutButtonDef(
                    id = ACTION_START_REC,
                    label = "START REC",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = true,
                    isClickable = false
                ),
                ShortcutButtonDef(
                    id = ACTION_STOP_REC,
                    label = "STOP REC",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = true,
                    isClickable = false
                ),
                ShortcutButtonDef(
                    id = ACTION_MUTE_MIC,
                    label = "MUTE MIC",
                    glowColorArgb = 0xFF00E5FF, // Cyan (or Red when active)
                    isSoon = false,
                    isClickable = true
                ),
                ShortcutButtonDef(
                    id = ACTION_LISTEN_MONITOR,
                    label = "LISTEN MONITOR",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = false,
                    isClickable = true
                )
            )
        ),

        // Page B: "QUALITY" (Reserved, not built yet)
        ShortcutPageDef(
            id = "QUALITY",
            title = "QUALITY",
            enabled = false,
            buttons = emptyList()
        ),

        // Page C: "REC / STREAM CONTROLS" (Reserved, not built yet)
        ShortcutPageDef(
            id = "REC_STREAM_CONTROLS",
            title = "REC / STREAM CONTROLS",
            enabled = false,
            buttons = emptyList()
        ),

        // Page D: "PNG OVERLAY"
        ShortcutPageDef(
            id = "PNG_OVERLAY",
            title = "PNG OVERLAY",
            enabled = true,
            buttons = listOf(
                ShortcutButtonDef(
                    id = ACTION_PNG_OVERLAY,
                    label = "PNG OVERLAY",
                    glowColorArgb = 0xFFF59E0B, // Amber
                    isSoon = false,
                    isClickable = true
                ),
                ShortcutButtonDef(
                    id = ACTION_FIT_FILL,
                    label = "FIT / FILL",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = false,
                    isClickable = true
                )
                // Remaining 4 grid slots left empty
            )
        ),

        // Page E: "CAMERA"
        ShortcutPageDef(
            id = "CAMERA",
            title = "CAMERA",
            enabled = true,
            buttons = listOf(
                ShortcutButtonDef(
                    id = ACTION_CAMERA_PIP,
                    label = "CAMERA PiP",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = false,
                    isClickable = true
                ),
                ShortcutButtonDef(
                    id = ACTION_FLIP_CAMERA,
                    label = "FLIP CAMERA",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = false,
                    isClickable = true
                ),
                ShortcutButtonDef(
                    id = ACTION_PIP_SIZE,
                    label = "PiP SIZE",
                    glowColorArgb = 0xFFF59E0B, // Amber
                    isSoon = false,
                    isClickable = true
                ),
                ShortcutButtonDef(
                    id = ACTION_ASPECT_RATIO,
                    label = "ASPECT RATIO",
                    glowColorArgb = 0xFF00E5FF, // Cyan
                    isSoon = false,
                    isClickable = true
                )
                // Remaining 2 grid slots left empty
            )
        )
    )

    fun getEnabledPages(): List<ShortcutPageDef> = ALL_PAGES.filter { it.enabled }

    fun getAllLabels(): List<String> {
        return getEnabledPages().flatMap { page ->
            page.buttons.map { it.label }
        }
    }
}
