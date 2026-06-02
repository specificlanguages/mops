package com.specificlanguages.mops.protocol

import com.google.gson.JsonParseException
import kotlin.test.Test
import kotlin.test.assertContains
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
        val inModelRequest = GsonCodec.toJson(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.InModel(
                    modelTarget = "/project/models/main.mps",
                    nodeId = "2110045694544566904",
                ),
            ),
            DaemonRequest::class.java,
        )
        assertContains(inModelRequest, """"type":"model-get-node"""")
        assertContains(inModelRequest, """"target"""")
        assertContains(inModelRequest, """"modelTarget":"/project/models/main.mps"""")
        assertContains(inModelRequest, """"nodeId":"2110045694544566904"""")
        assertEquals(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.InModel(
                    modelTarget = "/project/models/main.mps",
                    nodeId = "2110045694544566904",
                ),
            ),
            GsonCodec.fromJson(inModelRequest, DaemonRequest::class.java),
        )

        val nodeReferenceRequest = GsonCodec.toJson(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.NodeReference(
                    "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                ),
            ),
            DaemonRequest::class.java,
        )
        assertContains(nodeReferenceRequest, """"target"""")
        assertContains(
            nodeReferenceRequest,
            """"nodeReference":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"""",
        )
        assertEquals(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.NodeReference(
                    "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                ),
            ),
            GsonCodec.fromJson(nodeReferenceRequest, DaemonRequest::class.java),
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
    fun `list request and response JSON carry a semantic list tree`() {
        assertEquals(
            MpsListRequest(token = "secret", target = null, depth = 1),
            GsonCodec.fromJson(
                """{"type":"list","token":"secret","depth":1}""",
                DaemonRequest::class.java,
            ),
        )
        val serializedRequest = GsonCodec.toJson(
            MpsListRequest(
                token = "secret",
                target = listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"),
                depth = 1,
            ),
            DaemonRequest::class.java,
        )
        assertEquals(
            MpsListRequest(
                token = "secret",
                target = listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"),
                depth = 1,
            ),
            GsonCodec.fromJson(serializedRequest, DaemonRequest::class.java),
        )
        assertEquals(
            MpsListResponse(
                root = MpsListEntryJson(
                    type = "project",
                    name = "mps-json",
                    children = listOf(
                        MpsListEntryJson(
                            type = "module",
                            name = "com.specificlanguages.json",
                            moduleKind = "language",
                            reference = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)",
                        ),
                    ),
                ),
            ),
            GsonCodec.fromJson(
                """{"type":"list","root":{"type":"project","name":"mps-json","children":[{"type":"module","name":"com.specificlanguages.json","moduleKind":"language","reference":"f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"}]}}""",
                DaemonResponse::class.java,
            ),
        )
    }

    @Test
    fun `find-usages request and response JSON carry usage results`() {
        val target = NodeTarget.NodeReference(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
        )
        val request = FindUsagesRequest(token = "secret", target = target, limit = 100)
        val serializedRequest = GsonCodec.toJson(request, DaemonRequest::class.java)

        assertContains(serializedRequest, """"type":"find-usages"""")
        assertContains(serializedRequest, """"limit":100""")
        assertEquals(
            request,
            GsonCodec.fromJson(serializedRequest, DaemonRequest::class.java),
        )

        assertEquals(
            FindUsagesResponse(
                limit = 100,
                truncated = false,
                usages = listOf(
                    MpsNodeUsageJson(
                        role = "concept",
                        owner = MpsNodeSummaryJson(
                            type = "node",
                            name = "JsonObject",
                            concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                            reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
                        ),
                    ),
                ),
            ),
            GsonCodec.fromJson(
                """{"type":"usages","limit":100,"truncated":false,"usages":[{"role":"concept","owner":{"type":"node","name":"JsonObject","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","reference":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905"}}]}""",
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
