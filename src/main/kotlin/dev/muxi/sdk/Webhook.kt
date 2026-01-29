package dev.muxi.sdk

import kotlinx.serialization.json.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookVerificationException(message: String) : Exception(message)

data class ContentItem(val type: String, val text: String? = null, val file: Map<String, Any>? = null) {
    companion object {
        fun from(data: JsonObject) = ContentItem(
            type = data["type"]?.jsonPrimitive?.contentOrNull ?: "text",
            text = data["text"]?.jsonPrimitive?.contentOrNull
        )
    }
}

data class ErrorDetails(val code: String, val message: String, val trace: String? = null) {
    companion object {
        fun from(data: JsonObject) = ErrorDetails(
            code = data["code"]?.jsonPrimitive?.contentOrNull ?: "unknown",
            message = data["message"]?.jsonPrimitive?.contentOrNull ?: "Unknown error",
            trace = data["trace"]?.jsonPrimitive?.contentOrNull
        )
    }
}

data class Clarification(val question: String, val clarificationRequestId: String? = null, val originalMessage: String? = null) {
    companion object {
        fun from(data: JsonObject) = Clarification(
            question = data["clarification_question"]?.jsonPrimitive?.contentOrNull ?: "",
            clarificationRequestId = data["clarification_request_id"]?.jsonPrimitive?.contentOrNull,
            originalMessage = data["original_message"]?.jsonPrimitive?.contentOrNull
        )
    }
}

data class WebhookEvent(
    val requestId: String,
    val status: String,
    val timestamp: Long,
    val content: List<ContentItem>,
    val error: ErrorDetails?,
    val clarification: Clarification?,
    val formationId: String?,
    val userId: String?,
    val processingTime: Double?,
    val processingMode: String,
    val webhookUrl: String?,
    val raw: JsonObject
) {
    companion object {
        fun from(data: JsonObject): WebhookEvent {
            val content = data["response"]?.jsonArray?.mapNotNull { it.jsonObject.let { ContentItem.from(it) } } ?: emptyList()
            val error = data["error"]?.jsonObject?.let { ErrorDetails.from(it) }
            val clarification = if (data["status"]?.jsonPrimitive?.contentOrNull == "awaiting_clarification") Clarification.from(data) else null
            
            return WebhookEvent(
                requestId = data["id"]?.jsonPrimitive?.contentOrNull ?: "",
                status = data["status"]?.jsonPrimitive?.contentOrNull ?: "unknown",
                timestamp = data["timestamp"]?.jsonPrimitive?.longOrNull ?: 0,
                content = content,
                error = error,
                clarification = clarification,
                formationId = data["formation_id"]?.jsonPrimitive?.contentOrNull,
                userId = data["user_id"]?.jsonPrimitive?.contentOrNull,
                processingTime = data["processing_time"]?.jsonPrimitive?.doubleOrNull,
                processingMode = data["processing_mode"]?.jsonPrimitive?.contentOrNull ?: "async",
                webhookUrl = data["webhook_url"]?.jsonPrimitive?.contentOrNull,
                raw = data
            )
        }
    }
}

object Webhook {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    fun verifySignature(payload: String, signatureHeader: String?, secret: String, toleranceSeconds: Int = 300): Boolean {
        if (signatureHeader.isNullOrEmpty()) return false
        if (secret.isEmpty()) throw WebhookVerificationException("Webhook secret is required")
        
        val parts = try {
            signatureHeader.split(",").associate { part ->
                val (key, value) = part.split("=", limit = 2)
                key to value
            }
        } catch (_: Exception) { return false }
        
        val timestamp = parts["t"]?.toLongOrNull() ?: return false
        val signature = parts["v1"] ?: return false
        
        val currentTime = System.currentTimeMillis() / 1000
        if (kotlin.math.abs(currentTime - timestamp) > toleranceSeconds) return false
        
        val message = "$timestamp.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val expected = mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
        
        return expected == signature
    }
    
    fun parse(payload: String): WebhookEvent {
        return try {
            val data = json.parseToJsonElement(payload).jsonObject
            WebhookEvent.from(data)
        } catch (e: Exception) {
            throw WebhookVerificationException("Invalid JSON payload: ${e.message}")
        }
    }
    
    fun parse(data: JsonObject): WebhookEvent = WebhookEvent.from(data)
}
