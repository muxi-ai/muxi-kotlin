package dev.muxi.sdk

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Auth {
    fun generateHmacSignature(secretKey: String, method: String, path: String): Pair<String, Long> {
        val timestamp = System.currentTimeMillis() / 1000
        val signPath = path.split("?").first()
        val message = "$timestamp;$method;$signPath"
        
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        val signature = Base64.getEncoder().encodeToString(mac.doFinal(message.toByteArray()))
        
        return signature to timestamp
    }
    
    fun buildAuthHeader(keyId: String, secretKey: String, method: String, path: String): String {
        val (signature, timestamp) = generateHmacSignature(secretKey, method, path)
        return "MUXI-HMAC key=$keyId, timestamp=$timestamp, signature=$signature"
    }
}
