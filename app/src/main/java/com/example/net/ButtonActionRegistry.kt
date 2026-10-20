package com.example.net

import android.util.Log

typealias ButtonActionHandler = () -> Unit

/**
 * Registry on the Feed device for executing actions triggered by the Remote shortcut buttons.
 * Uses stable string keys matching ShortcutConfig.
 */
class ButtonActionRegistry {
    companion object {
        private const val TAG = "ButtonActionRegistry"
    }

    private val handlers = mutableMapOf<String, ButtonActionHandler>()

    fun registerAction(actionId: String, handler: ButtonActionHandler) {
        handlers[actionId] = handler
    }

    fun getLabels(): List<String> {
        return ShortcutConfig.getAllLabels()
    }

    fun execute(actionId: String) {
        Log.i(TAG, "Executing shortcut button action: $actionId")
        val handler = handlers[actionId]
        if (handler != null) {
            try {
                handler()
            } catch (e: Throwable) {
                Log.e(TAG, "Error executing action $actionId: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "No custom handler registered for action $actionId")
        }
    }
}
