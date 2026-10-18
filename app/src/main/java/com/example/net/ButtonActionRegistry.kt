package com.example.net

import android.util.Log

typealias ButtonActionHandler = (buttonId: Int) -> Unit

/**
 * Registry on the Feed device for executing actions triggered by the 6 Remote shortcut buttons.
 */
class ButtonActionRegistry {
    companion object {
        private const val TAG = "ButtonActionRegistry"
    }

    private val handlers = mutableMapOf<Int, ButtonActionHandler>()
    private val buttonLabels = mutableMapOf(
        1 to "Button 1",
        2 to "Button 2",
        3 to "Button 3",
        4 to "Button 4",
        5 to "Button 5",
        6 to "Button 6"
    )

    fun registerAction(buttonId: Int, label: String? = null, handler: ButtonActionHandler) {
        handlers[buttonId] = handler
        label?.let { buttonLabels[buttonId] = it }
    }

    fun setLabel(buttonId: Int, label: String) {
        buttonLabels[buttonId] = label
    }

    fun getLabels(): List<String> {
        return (1..6).map { buttonLabels[it] ?: "Button $it" }
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
