package org.muxi.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SseEventParser {
    private var currentEvent: String? = null
    private val dataParts = mutableListOf<String>()

    fun processLine(line: String): SseEvent? {
        if (line.startsWith(":")) return null
        if (line.isEmpty()) return flush()

        val (field, value) = splitField(line)
        when (field) {
            "event" -> currentEvent = value
            "data" -> dataParts += value
        }
        return null
    }

    fun flush(): SseEvent? {
        if (currentEvent == null && dataParts.isEmpty()) return null
        val event = SseEvent(currentEvent ?: "message", dataParts.joinToString("\n"))
        currentEvent = null
        dataParts.clear()
        return event
    }

    companion object {
        fun throwIfRouteError(event: SseEvent) {
            if (event.event != "error") return

            var code = "STREAM_ERROR"
            var message = event.data.ifEmpty { "stream error" }
            var details: Map<String, Any?>? = null

            try {
                val payload = Json.parseToJsonElement(event.data).jsonObject
                details = payload.mapValues { it.value.toString().trim('"') }
                code = payload["type"]?.jsonPrimitive?.contentOrNull
                    ?: payload["code"]?.jsonPrimitive?.contentOrNull
                    ?: payload["error"]?.jsonPrimitive?.contentOrNull
                    ?: code
                message = payload["error"]?.jsonPrimitive?.contentOrNull
                    ?: payload["message"]?.jsonPrimitive?.contentOrNull
                    ?: message
            } catch (_: Exception) {
            }

            throw MuxiException(code, message, 0, details)
        }

        private fun splitField(line: String): Pair<String, String> {
            val idx = line.indexOf(':')
            if (idx < 0) return line to ""
            var value = line.substring(idx + 1)
            if (value.startsWith(" ")) value = value.substring(1)
            return line.substring(0, idx) to value
        }
    }
}
