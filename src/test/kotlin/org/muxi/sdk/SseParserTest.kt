package org.muxi.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class SseParserTest {
    @Test
    fun `flushes event-only done frames`() {
        val parser = SseEventParser()

        parser.processLine(": keepalive")
        parser.processLine("")
        parser.processLine("event: done")
        val event = parser.processLine("")

        assertNotNull(event)
        assertEquals("done", event.event)
        assertEquals("", event.data)
    }

    @Test
    fun `preserves multiline data`() {
        val parser = SseEventParser()

        parser.processLine("event: planning")
        parser.processLine("data: one")
        parser.processLine("data: two")
        val event = parser.processLine("")

        assertNotNull(event)
        assertEquals("planning", event.event)
        assertEquals("one\ntwo", event.data)
    }

    @Test
    fun `route level errors throw muxi exception`() {
        val error = assertFailsWith<MuxiException> {
            SseEventParser.throwIfRouteError(SseEvent("error", """{"error":"boom","type":"RUNTIME_ERROR"}"""))
        }

        assertEquals("RUNTIME_ERROR", error.errorCode)
        assertEquals(0, error.statusCode)
    }
}
