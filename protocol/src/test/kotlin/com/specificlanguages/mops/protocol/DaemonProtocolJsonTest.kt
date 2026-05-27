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
        assertEquals(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.InModel(
                    modelTarget = "/project/models/main.mps",
                    nodeId = "2110045694544566904",
                ),
            ),
            GsonCodec.fromJson(
                """{"type":"model-get-node","token":"secret","target":{"modelTarget":"/project/models/main.mps","nodeId":"2110045694544566904"}}""",
                DaemonRequest::class.java,
            ),
        )
    }

    @Test
    fun `get-node target JSON is nested under the daemon request`() {
        assertEquals(
            """{"type":"model-get-node","token":"secret","target":{"modelTarget":"/project/models/main.mps","nodeId":"2110045694544566904"}}""",
            GsonCodec.toJson(
                ModelGetNodeRequest(
                    token = "secret",
                    target = NodeTarget.InModel(
                        modelTarget = "/project/models/main.mps",
                        nodeId = "2110045694544566904",
                    ),
                ),
                DaemonRequest::class.java,
            ),
        )
        assertEquals(
            """{"type":"model-get-node","token":"secret","target":{"nodeReference":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"}}""",
            GsonCodec.toJson(
                ModelGetNodeRequest(
                    token = "secret",
                    target = NodeTarget.NodeReference(
                        "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                    ),
                ),
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
        assertEquals(
            ModelGetNodeResponse(
                node = MpsNodeJson(
                    model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    id = "2110045694544566904",
                ),
            ),
            GsonCodec.fromJson(
                """{"type":"model-get-node","node":{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}}""",
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
