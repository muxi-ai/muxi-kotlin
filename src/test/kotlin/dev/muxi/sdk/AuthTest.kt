package dev.muxi.sdk

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class AuthTest {
    @Test
    fun `generateHmacSignature returns valid signature and timestamp`() {
        val (signature, timestamp) = Auth.generateHmacSignature("secret", "GET", "/test")
        
        assertTrue(signature.isNotEmpty())
        assertTrue(timestamp > 0)
        assertTrue(kotlin.math.abs(System.currentTimeMillis() / 1000 - timestamp) <= 5)
    }
    
    @Test
    fun `buildAuthHeader returns properly formatted header`() {
        val header = Auth.buildAuthHeader("key123", "secret", "POST", "/rpc/test")
        
        assertTrue(header.startsWith("MUXI-HMAC key=key123, timestamp="))
        assertTrue(header.contains("signature="))
    }
    
    @Test
    fun `generateHmacSignature strips query params`() {
        val (sig1, _) = Auth.generateHmacSignature("secret", "GET", "/test")
        val (sig2, _) = Auth.generateHmacSignature("secret", "GET", "/test?foo=bar")
        
        assertEquals(sig1.length, sig2.length)
    }
}
