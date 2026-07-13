package org.muxi.sdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SseParserTest {
    @Test
    fun `parses widgets from a ui frame`() {
        val event = SseEvent(
            "ui",
            "{\"ui\":[{\"type\":\"options\",\"id\":\"w1\",\"prompt\":\"Which?\"," +
                "\"options\":[{\"value\":\"us\",\"label\":\"United States\"}]}," +
                "{\"type\":\"action_link\",\"id\":\"w2\",\"label\":\"Dash\",\"url\":\"https://x.io\"}]}"
        )

        val widgets = parseUiWidgets(event)

        assertEquals(2, widgets.size)
        assertEquals("options", widgets[0].jsonObject["type"]?.jsonPrimitive?.content)
        assertEquals(
            "United States",
            widgets[0].jsonObject["options"]?.jsonArray?.get(0)?.jsonObject?.get("label")?.jsonPrimitive?.content
        )
        assertEquals("https://x.io", widgets[1].jsonObject["url"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parse ui widgets ignores other frames`() {
        assertTrue(parseUiWidgets(SseEvent("message", "hi")).isEmpty())
        assertTrue(parseUiWidgets(SseEvent("ui", "not json")).isEmpty())
        assertTrue(parseUiWidgets(SseEvent("ui", "{\"ui\":{}}")).isEmpty())
    }

    @Test
    fun `unwrap envelope surfaces idempotency key`() {
        val transport = FormationTransport("http://example.com", "admin-key", "client-key", 30, 0, false)
        val env = Json.parseToJsonElement(
            "{\"object\":\"api_response\",\"timestamp\":123," +
                "\"request\":{\"id\":\"req-1\",\"idempotency_key\":\"idem-42\"}," +
                "\"data\":{\"foo\":\"bar\"},\"success\":true}"
        )

        val out = transport.unwrapEnvelope(env)!!.jsonObject

        assertEquals("bar", out["foo"]?.jsonPrimitive?.content)
        assertEquals("req-1", out["request_id"]?.jsonPrimitive?.content)
        assertEquals("idem-42", out["idempotency_key"]?.jsonPrimitive?.content)
    }

    @Test
    fun `unwrap envelope omits idempotency key when absent`() {
        val transport = FormationTransport("http://example.com", "admin-key", "client-key", 30, 0, false)
        val env = Json.parseToJsonElement(
            "{\"object\":\"api_response\",\"request\":{\"id\":\"req-1\"}," +
                "\"data\":{\"foo\":\"bar\"},\"success\":true}"
        )

        val out = transport.unwrapEnvelope(env)!!.jsonObject

        assertFalse(out.containsKey("idempotency_key"))
    }

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
