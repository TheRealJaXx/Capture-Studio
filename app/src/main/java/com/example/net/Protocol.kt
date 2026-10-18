package com.example.net

import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol constants and JSON message serializers/deserializers for Capture Studio Control Link.
 */
object Protocol {
    const val CONTROL_PORT = 45454
    const val AUDIO_UDP_PORT = 45455
    const val SERVICE_TYPE = "_capturestudio._tcp."
    const val SERVICE_NAME_PREFIX = "CaptureStudioFeed"

    // Message Types
    const val TYPE_HELLO = "hello"
    const val TYPE_HELLO_ACK = "hello_ack"
    const val TYPE_STATE = "state"
    const val TYPE_SET_GAIN = "set_gain"
    const val TYPE_SET_MUTE = "set_mute"
    const val TYPE_BUTTON = "button"
    const val TYPE_METER = "meter"
    const val TYPE_PING = "ping"
    const val TYPE_PONG = "pong"
    const val TYPE_AUDIO_START = "audio_start"
    const val TYPE_AUDIO_STOP = "audio_stop"
    const val TYPE_SET_LOCAL_MONITOR = "set_local_monitor"

    // Sources
    const val SRC_MIC = "mic"
    const val SRC_CAPTURE = "capture"

    data class SourceMeter(
        val rms: Float = -60f,
        val peak: Float = -60f
    ) {
        companion object {
            val SILENCE = SourceMeter(-60f, -60f)
        }
    }

    data class FeedState(
        val captureGain: Float = 1.0f,
        val captureMuted: Boolean = false,
        val micGain: Float = 1.0f,
        val micMuted: Boolean = false,
        val micAvailable: Boolean = true,
        val localMonitor: Boolean = true,
        val audioStreaming: Boolean = false,
        val buttonLabels: List<String> = listOf(
            "Button 1", "Button 2", "Button 3", "Button 4", "Button 5", "Button 6"
        )
    )

    fun createHelloMessage(pin: String, token: String?): String {
        return JSONObject().apply {
            put("t", TYPE_HELLO)
            put("pin", pin)
            token?.let { put("token", it) }
        }.toString()
    }

    fun createHelloAckMessage(ok: Boolean, token: String?, error: String?): String {
        return JSONObject().apply {
            put("t", TYPE_HELLO_ACK)
            put("ok", ok)
            token?.let { put("token", it) }
            error?.let { put("error", it) }
        }.toString()
    }

    fun createStateMessage(state: FeedState): String {
        return JSONObject().apply {
            put("t", TYPE_STATE)
            put("captureGain", state.captureGain.toDouble())
            put("captureMuted", state.captureMuted)
            put("micGain", state.micGain.toDouble())
            put("micMuted", state.micMuted)
            put("micAvailable", state.micAvailable)
            put("localMonitor", state.localMonitor)
            put("audioStreaming", state.audioStreaming)
            val arr = JSONArray()
            state.buttonLabels.forEach { arr.put(it) }
            put("buttonLabels", arr)
        }.toString()
    }

    fun parseStateMessage(json: JSONObject): FeedState {
        val buttonLabels = mutableListOf<String>()
        if (json.has("buttonLabels")) {
            val arr = json.getJSONArray("buttonLabels")
            for (i in 0 until arr.length()) {
                buttonLabels.add(arr.getString(i))
            }
        } else {
            buttonLabels.addAll(listOf("Button 1", "Button 2", "Button 3", "Button 4", "Button 5", "Button 6"))
        }

        return FeedState(
            captureGain = json.optDouble("captureGain", 1.0).toFloat(),
            captureMuted = json.optBoolean("captureMuted", false),
            micGain = json.optDouble("micGain", 1.0).toFloat(),
            micMuted = json.optBoolean("micMuted", false),
            micAvailable = json.optBoolean("micAvailable", true),
            localMonitor = json.optBoolean("localMonitor", true),
            audioStreaming = json.optBoolean("audioStreaming", false),
            buttonLabels = buttonLabels
        )
    }

    fun createMeterMessage(mic: SourceMeter, capture: SourceMeter): String {
        return JSONObject().apply {
            put("t", TYPE_METER)
            put("mic", JSONObject().apply {
                put("rms", mic.rms.toDouble())
                put("peak", mic.peak.toDouble())
            })
            put("capture", JSONObject().apply {
                put("rms", capture.rms.toDouble())
                put("peak", capture.peak.toDouble())
            })
        }.toString()
    }

    fun parseMeterMessage(json: JSONObject): Pair<SourceMeter, SourceMeter> {
        val micObj = json.optJSONObject("mic")
        val capObj = json.optJSONObject("capture")

        val mic = if (micObj != null) {
            SourceMeter(
                rms = micObj.optDouble("rms", -60.0).toFloat(),
                peak = micObj.optDouble("peak", -60.0).toFloat()
            )
        } else SourceMeter.SILENCE

        val cap = if (capObj != null) {
            SourceMeter(
                rms = capObj.optDouble("rms", -60.0).toFloat(),
                peak = capObj.optDouble("peak", -60.0).toFloat()
            )
        } else SourceMeter.SILENCE

        return Pair(mic, cap)
    }

    fun createSetGainMessage(src: String, value: Float): String {
        return JSONObject().apply {
            put("t", TYPE_SET_GAIN)
            put("src", src)
            put("v", value.toDouble())
        }.toString()
    }

    fun createSetMuteMessage(src: String, value: Boolean): String {
        return JSONObject().apply {
            put("t", TYPE_SET_MUTE)
            put("src", src)
            put("v", value)
        }.toString()
    }

    fun createButtonMessage(id: Int): String {
        return JSONObject().apply {
            put("t", TYPE_BUTTON)
            put("id", id)
        }.toString()
    }

    fun createSetLocalMonitorMessage(enabled: Boolean): String {
        return JSONObject().apply {
            put("t", TYPE_SET_LOCAL_MONITOR)
            put("v", enabled)
        }.toString()
    }

    fun createAudioStartMessage(): String {
        return JSONObject().apply { put("t", TYPE_AUDIO_START) }.toString()
    }

    fun createAudioStopMessage(): String {
        return JSONObject().apply { put("t", TYPE_AUDIO_STOP) }.toString()
    }

    fun createPingMessage(): String {
        return JSONObject().apply { put("t", TYPE_PING) }.toString()
    }

    fun createPongMessage(): String {
        return JSONObject().apply { put("t", TYPE_PONG) }.toString()
    }
}
