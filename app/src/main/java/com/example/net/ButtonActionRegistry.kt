package com.example.net

import android.util.Log

typealias ButtonActionHandler = (buttonId: Int) -> Unit

/**
 * Registry on the Feed device for executing actions triggered by the Remote shortcut buttons.
 * Supports multiple pages of shortcuts (18+ buttons).
 */
class ButtonActionRegistry {
    companion object {
        private const val TAG = "ButtonActionRegistry"
    }

    private val handlers = mutableMapOf<Int, ButtonActionHandler>()
    private val buttonLabels = mutableMapOf<Int, String>()

    init {
        // Default labels for Page 1 (Stream & Audio)
        buttonLabels[1] = "START STREAM"
        buttonLabels[2] = "START REC"
        buttonLabels[3] = "MUTE MIC"
        buttonLabels[4] = "LISTEN MON"
        buttonLabels[5] = "SCENE: LIVE"
        buttonLabels[6] = "AD READ"

        // Default labels for Page 2 (Camera & PiP)
        buttonLabels[7] = "CAMERA PiP"
        buttonLabels[8] = "FLIP CAMERA"
        buttonLabels[9] = "PiP SIZE"
        buttonLabels[10] = "ASPECT RATIO"
        buttonLabels[11] = "PNG OVERLAY"
        buttonLabels[12] = "QUALITY MODE"

        // Default labels for Page 3 (Studio Tools & Presets)
        buttonLabels[13] = "FIT / FILL"
        buttonLabels[14] = "HUD STATS"
        buttonLabels[15] = "SCENE: CAM ONLY"
        buttonLabels[16] = "MUTE GAME"
        buttonLabels[17] = "AUDIO RESET"
        buttonLabels[18] = "END STREAM"
    }

    fun registerAction(buttonId: Int, label: String? = null, handler: ButtonActionHandler) {
        handlers[buttonId] = handler
        label?.let { buttonLabels[buttonId] = it }
    }

    fun setLabel(buttonId: Int, label: String) {
        buttonLabels[buttonId] = label
    }

    fun getLabels(): List<String> {
        val maxId = maxOf(18, buttonLabels.keys.maxOrNull() ?: 18)
        return (1..maxId).map { buttonLabels[it] ?: "Button $it" }
    }

    fun execute(buttonId: Int) {
        Log.i(TAG, "Executing shortcut button action: ID $buttonId (${buttonLabels[buttonId]})")
        val handler = handlers[buttonId]
        if (handler != null) {
            try {
                handler(buttonId)
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing button $buttonId action: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "No custom handler registered for button $buttonId (logged default)")
        }
    }
}
