package com.specificlanguages.mops.protocol

import com.google.gson.JsonParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DaemonProtocolJsonTest {
    @Test
    fun `request JSON decodes to concrete daemon request messages`() {
        assertEquals(
            PingRequest(token = "secret"),
            GsonCodec.fromJson(
                """{"type":"ping","token":"secret"}""",
                DaemonRequest::class.java,
            ),
        )
        assertEquals(
            StopRequest(token = "secret"),
            GsonCodec.fromJson(
                """{"type":"stop","token":"secret"}""",
                DaemonRequest::class.java,
            ),
        )
        assertEquals(
            ModelResaveRequest(token = "secret", modelTarget = "/project/models/main.mps"),
            GsonCodec.fromJson(
                """{"type":"model-resave","token":"secret","modelTarget":"/project/models/main.mps"}""",
                DaemonRequest::class.java,
            ),
        )
    }

    @Test
    fun `response JSON decodes to concrete daemon response messages`() {
        assertEquals(
            ReadyMessage(port = 3210),
            GsonCodec.fromJson(
                """{"type":"ready","port":3210}""",
                DaemonResponse::class.java,
            ),
        )
        assertEquals(
            DaemonErrorResponse(
                errorCode = "NOT_IMPLEMENTED",
                message = "not wired yet",
                workspacePath = "/state",
            ),
            GsonCodec.fromJson(
                """{"type":"error","errorCode":"NOT_IMPLEMENTED","message":"not wired yet","workspacePath":"/state"}""",
                DaemonResponse::class.java,
            ),
        )
    }

    @Test
    fun `message adapters require a type discriminator`() {
        val exception = assertFailsWith<JsonParseException> {
            GsonCodec.fromJson("""{"token":"secret"}""", DaemonRequest::class.java)
        }

        assertEquals("request type is required", exception.message)
    }
}
