package dev.muxi.sdk

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.*

class WebhookTest {
    private val secret = "test_webhook_secret"
    private val payload = """{"id":"req123","status":"completed","response":[{"type":"text","text":"Hello"}]}"""
    
    private fun createSignature(payload: String, secret: String, timestamp: Long? = null): String {
        val ts = timestamp ?: (System.currentTimeMillis() / 1000)
        val message = "$ts.$payload"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val signature = mac.doFinal(message.toByteArray()).joinToString("") { "%02x".format(it) }
        return "t=$ts,v1=$signature"
    }
    
    @Test
    fun `verifySignature returns true for valid signature`() {
        val sigHeader = createSignature(payload, secret)
        assertTrue(Webhook.verifySignature(payload, sigHeader, secret))
    }
    
    @Test
    fun `verifySignature returns false for invalid signature`() {
        val sigHeader = "t=${System.currentTimeMillis() / 1000},v1=invalidsignature"
        assertFalse(Webhook.verifySignature(payload, sigHeader, secret))
    }
    
    @Test
    fun `verifySignature returns false for null header`() {
        assertFalse(Webhook.verifySignature(payload, null, secret))
    }
    
    @Test
    fun `verifySignature returns false for empty header`() {
        assertFalse(Webhook.verifySignature(payload, "", secret))
    }
    
    @Test
    fun `verifySignature returns false for expired timestamp`() {
        val oldTimestamp = System.currentTimeMillis() / 1000 - 600
        val sigHeader = createSignature(payload, secret, oldTimestamp)
        assertFalse(Webhook.verifySignature(payload, sigHeader, secret))
    }
    
    @Test
    fun `verifySignature throws for missing secret`() {
        assertFailsWith<WebhookVerificationException> {
            Webhook.verifySignature(payload, "t=123,v1=abc", "")
        }
    }
    
    @Test
    fun `parse completed payload`() {
        val event = Webhook.parse(payload)
        
        assertEquals("req123", event.requestId)
        assertEquals("completed", event.status)
        assertEquals(1, event.content.size)
        assertEquals("text", event.content[0].type)
        assertEquals("Hello", event.content[0].text)
    }
    
    @Test
    fun `parse failed payload`() {
        val failedPayload = """{"id":"req456","status":"failed","error":{"code":"TIMEOUT","message":"Request timed out"}}"""
        val event = Webhook.parse(failedPayload)
        
        assertEquals("failed", event.status)
        assertNotNull(event.error)
        assertEquals("TIMEOUT", event.error?.code)
        assertEquals("Request timed out", event.error?.message)
    }
    
    @Test
    fun `parse clarification payload`() {
        val clarificationPayload = """{"id":"req789","status":"awaiting_clarification","clarification_question":"Which file do you mean?"}"""
        val event = Webhook.parse(clarificationPayload)
        
        assertEquals("awaiting_clarification", event.status)
        assertNotNull(event.clarification)
        assertEquals("Which file do you mean?", event.clarification?.question)
    }
    
    @Test
    fun `parse invalid json throws`() {
        assertFailsWith<WebhookVerificationException> {
            Webhook.parse("not json")
        }
    }
}
