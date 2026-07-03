package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DaemonProtocolJsonTest {
    @Test
    fun `request JSON decodes to concrete daemon request messages`() {
        assertEquals(
            PingRequest(token = "secret"),
            ProtocolJson.decodeRequest("""{"type":"ping","token":"secret"}"""),
        )
        assertEquals(
            StopRequest(token = "secret"),
            ProtocolJson.decodeRequest("""{"type":"stop","token":"secret"}"""),
        )
        assertEquals(
            ModelResaveRequest(token = "secret", modelTarget = "/project/models/main.mps"),
            ProtocolJson.decodeRequest(
                """{"type":"model-resave","token":"secret","modelTarget":"/project/models/main.mps"}""",
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
            ProtocolJson.decodeRequest(
                """{"type":"model-get-node","token":"secret","target":{"type":"inModel","modelTarget":"/project/models/main.mps","nodeId":"2110045694544566904"}}""",
            ),
        )
    }

    @Test
    fun `get-node target JSON is nested under the daemon request`() {
        val inModelRequest = ProtocolJson.encodeRequest(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.InModel(
                    modelTarget = "/project/models/main.mps",
                    nodeId = "2110045694544566904",
                ),
            ),
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
            ProtocolJson.decodeRequest(inModelRequest),
        )

        val nodeReferenceRequest = ProtocolJson.encodeRequest(
            ModelGetNodeRequest(
                token = "secret",
                target = NodeTarget.NodeReference(
                    "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                ),
            ),
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
            ProtocolJson.decodeRequest(nodeReferenceRequest),
        )
    }

    @Test
    fun `model edit request and response JSON carry set-property batches`() {
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val request = ModelEditRequest(
            token = "secret",
            batch = EditBatch(
                operations = listOf(
                    EditOperation.SetProperty(
                        target = EditTarget.NodeReference(nodeReference),
                        name = "name",
                        value = "RenamedConcept",
                    ),
                    EditOperation.SetProperty(
                        target = EditTarget.InModel(
                            modelTarget = "/project/models/main.mps",
                            nodeId = "2110045694544566905",
                        ),
                        name = "description",
                        value = null,
                    ),
                ),
            ),
        )
        val serializedRequest = ProtocolJson.encodeRequest(request)

        assertContains(serializedRequest, """"type":"model-edit"""")
        assertContains(serializedRequest, """"op":"setProperty"""")
        assertContains(serializedRequest, """"target":"$nodeReference"""")
        assertContains(serializedRequest, """"target":{"model":"/project/models/main.mps","nodeId":"2110045694544566905"}""")
        assertEquals(
            request,
            ProtocolJson.decodeRequest(serializedRequest),
        )

        val response = ModelEditResponse(
            created = mapOf("\$new" to "$nodeReference-copy"),
            violations = listOf(
                EditConstraintViolation(
                    operation = 0,
                    constraint = "property",
                    message = "not checked in this slice",
                ),
            ),
        )
        val serializedResponse = ProtocolJson.encodeResponse(response)

        assertContains(serializedResponse, """"type":"model-edit"""")
        assertContains(serializedResponse, "\"created\":{\"\$new\":\"$nodeReference-copy\"}")
        assertEquals(
            response,
            ProtocolJson.decodeResponse(serializedResponse),
        )
    }

    @Test
    fun `model edit request JSON carries the constraints mode and defaults it to best-effort`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val batch = EditBatch(operations = listOf(EditOperation.Delete(EditTarget.NodeReference("$model/1"))))

        val advisory = ProtocolJson.encodeRequest(
            ModelEditRequest(token = "secret", batch = batch, constraints = ConstraintEnforcement.ADVISORY),
        )
        assertContains(advisory, """"constraints":"advisory"""")
        assertEquals(
            ModelEditRequest(token = "secret", batch = batch, constraints = ConstraintEnforcement.ADVISORY),
            ProtocolJson.decodeRequest(advisory),
        )

        assertEquals(
            ModelEditRequest(token = "secret", batch = batch, constraints = ConstraintEnforcement.BEST_EFFORT),
            ProtocolJson.decodeRequest(
                """{"type":"model-edit","token":"secret","batch":{"operations":[{"op":"delete","target":"$model/1"}]}}""",
            ),
        )
    }

    @Test
    fun `read markers round-trip and stay off the happy path`() {
        val happy = ProtocolJson.encodeResponse(
            ModelGetNodeResponse(
                MpsNodeJson(
                    concept = "c",
                    references = listOf(
                        MpsNodeReferenceJson("r", MpsNodeReferenceTargetJson(model = "m", node = "1", name = "N", concept = "c")),
                    ),
                ),
            ),
        )
        assertFalse(happy.contains("resolved"), happy)
        assertFalse(happy.contains("conceptValid"), happy)

        val node = MpsNodeJson(
            concept = "com.example.Foo",
            conceptValid = false,
            references = listOf(
                MpsNodeReferenceJson("dangling", MpsNodeReferenceTargetJson(node = "404", resolved = false)),
                MpsNodeReferenceJson(
                    "stale",
                    MpsNodeReferenceTargetJson(model = "m", node = "2", name = "N", concept = "c", conceptValid = false),
                ),
            ),
        )
        val encoded = ProtocolJson.encodeResponse(ModelGetNodeResponse(node))
        assertContains(encoded, """"resolved":false""")
        assertContains(encoded, """"conceptValid":false""")
        assertEquals(ModelGetNodeResponse(node), ProtocolJson.decodeResponse(encoded))
    }

    @Test
    fun `model edit request JSON round-trips structural operations and inline subtrees`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val request = ModelEditRequest(
            token = "secret",
            batch = EditBatch(
                operations = listOf(
                    EditOperation.AddChild(
                        target = EditTarget.NodeReference("$model/100"),
                        role = "members",
                        concept = "com.acme.Block",
                        properties = listOf(MpsNodePropertyJson(name = "name", value = "b")),
                        references = listOf(
                            MpsNodeReferenceJson(
                                role = "type",
                                target = MpsNodeReferenceTargetJson(model = model, node = "77"),
                            ),
                        ),
                        children = listOf(
                            MpsNodeJson(
                                role = "statements",
                                concept = "com.acme.Return",
                                properties = listOf(MpsNodePropertyJson(name = "label", value = "r")),
                            ),
                        ),
                    ),
                    EditOperation.Delete(target = EditTarget.InModel(modelTarget = model, nodeId = "200")),
                    EditOperation.DeleteChild(
                        target = EditTarget.NodeReference("$model/300"),
                        role = "members",
                        position = ChildPosition.Index(2),
                    ),
                    EditOperation.MoveNode(
                        target = EditTarget.NodeReference("$model/400"),
                        into = EditTarget.NodeReference("$model/500"),
                        role = "members",
                        position = ChildPosition.First,
                    ),
                ),
            ),
        )

        val serialized = ProtocolJson.encodeRequest(request)

        assertContains(serialized, """"op":"addChild"""")
        assertContains(serialized, """"op":"delete"""")
        assertContains(serialized, """"op":"deleteChild"""")
        assertContains(serialized, """"op":"moveNode"""")
        assertContains(serialized, """"position":2""")
        assertContains(serialized, """"position":"first"""")
        assertEquals(request, ProtocolJson.decodeRequest(serialized))
    }

    @Test
    fun `model edit request JSON round-trips reference and copy operations`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val request = ModelEditRequest(
            token = "secret",
            batch = EditBatch(
                operations = listOf(
                    EditOperation.SetReference(
                        target = EditTarget.NodeReference("$model/1"),
                        role = "dataType",
                        to = EditTarget.NodeReference("$model/2"),
                    ),
                    EditOperation.SetReference(
                        target = EditTarget.InModel(modelTarget = model, nodeId = "3"),
                        role = "dataType",
                        to = null,
                    ),
                    EditOperation.CopyNode(
                        target = EditTarget.NodeReference("$model/4"),
                        source = EditTarget.NodeReference("$model/5"),
                        role = "propertyDeclaration",
                        position = ChildPosition.Last,
                    ),
                ),
            ),
        )

        val serialized = ProtocolJson.encodeRequest(request)

        assertContains(serialized, """"op":"setReference"""")
        assertContains(serialized, """"op":"copyNode"""")
        assertContains(serialized, """"to":"$model/2"""")
        assertContains(serialized, """"source":"$model/5"""")
        assertEquals(request, ProtocolJson.decodeRequest(serialized))
    }

    @Test
    fun `model edit request JSON round-trips aliases as declaration and target`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val request = ModelEditRequest(
            token = "secret",
            batch = EditBatch(
                operations = listOf(
                    EditOperation.AddChild(
                        target = EditTarget.NodeReference("$model/1"),
                        role = "propertyDeclaration",
                        concept = "jetbrains.mps.lang.structure.structure.PropertyDeclaration",
                        alias = "\$p",
                    ),
                    EditOperation.SetProperty(target = EditTarget.Alias("\$p"), name = "name", value = "aliased"),
                    EditOperation.SetReference(
                        target = EditTarget.NodeReference("$model/2"),
                        role = "dataType",
                        to = EditTarget.Alias("\$p"),
                    ),
                ),
            ),
        )

        val serialized = ProtocolJson.encodeRequest(request)

        assertContains(serialized, """"as":"${'$'}p"""")
        assertContains(serialized, """"target":"${'$'}p"""")
        assertContains(serialized, """"to":"${'$'}p"""")
        assertEquals(request, ProtocolJson.decodeRequest(serialized))
    }

    @Test
    fun `child position JSON encodes ordinals as strings and index as an integer`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        fun roundTrip(position: ChildPosition): EditOperation {
            val request = ModelEditRequest(
                token = "secret",
                batch = EditBatch(
                    operations = listOf(
                        EditOperation.DeleteChild(
                            target = EditTarget.NodeReference("$model/1"),
                            role = "members",
                            position = position,
                        ),
                    ),
                ),
            )
            return (ProtocolJson.decodeRequest(ProtocolJson.encodeRequest(request)) as ModelEditRequest)
                .batch.operations.single()
        }

        assertEquals(ChildPosition.First, (roundTrip(ChildPosition.First) as EditOperation.DeleteChild).position)
        assertEquals(ChildPosition.Last, (roundTrip(ChildPosition.Last) as EditOperation.DeleteChild).position)
        assertEquals(ChildPosition.Only, (roundTrip(ChildPosition.Only) as EditOperation.DeleteChild).position)
        assertEquals(ChildPosition.Index(3), (roundTrip(ChildPosition.Index(3)) as EditOperation.DeleteChild).position)
    }

    @Test
    fun `response JSON decodes to concrete daemon response messages`() {
        assertEquals(
            ReadyMessage(port = 3210),
            ProtocolJson.decodeResponse("""{"type":"ready","port":3210}"""),
        )
        assertEquals(
            DaemonErrorResponse(
                errorCode = "NOT_IMPLEMENTED",
                message = "not wired yet",
                workspacePath = "/state",
            ),
            ProtocolJson.decodeResponse(
                """{"type":"error","errorCode":"NOT_IMPLEMENTED","message":"not wired yet","workspacePath":"/state"}""",
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
            ProtocolJson.decodeResponse(
                """{"type":"model-get-node","node":{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}}""",
            ),
        )
    }

    @Test
    fun `get-node response JSON carries enriched and bare reference targets`() {
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val response = ModelGetNodeResponse(
            node = MpsNodeJson(
                model = model,
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                id = "2110045694544566904",
                references = listOf(
                    MpsNodeReferenceJson(
                        role = "extends",
                        target = MpsNodeReferenceTargetJson(
                            model = "r:00000000-0000-4000-0000-011c89590288(jetbrains.mps.lang.core.structure)",
                            node = "1169194658468",
                            name = "BaseConcept",
                            concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                        ),
                    ),
                    MpsNodeReferenceJson(
                        role = "dangling",
                        target = MpsNodeReferenceTargetJson(node = "404"),
                    ),
                ),
            ),
        )

        val serialized = ProtocolJson.encodeResponse(response)

        assertContains(serialized, """"name":"BaseConcept"""")
        assertContains(serialized, """"concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration"""")
        assertEquals(response, ProtocolJson.decodeResponse(serialized))
    }

    @Test
    fun `reference target JSON keeps name and concept optional`() {
        assertEquals(
            MpsNodeReferenceTargetJson(model = "m", node = "1"),
            ProtocolJson.decodeResponse(
                """{"type":"model-get-node","node":{"concept":"c","references":[{"role":"r","target":{"model":"m","node":"1"}}]}}""",
            ).let { (it as ModelGetNodeResponse).node.references!!.single().target },
        )
    }

    @Test
    fun `list request and response JSON carry a semantic list tree`() {
        assertEquals(
            MpsListRequest(token = "secret", target = null, depth = 1),
            ProtocolJson.decodeRequest("""{"type":"list","token":"secret","depth":1}"""),
        )
        val serializedRequest = ProtocolJson.encodeRequest(
            MpsListRequest(
                token = "secret",
                target = listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"),
                depth = 1,
            ),
        )
        assertEquals(
            MpsListRequest(
                token = "secret",
                target = listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"),
                depth = 1,
            ),
            ProtocolJson.decodeRequest(serializedRequest),
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
            ProtocolJson.decodeResponse(
                """{"type":"list","root":{"type":"project","name":"mps-json","children":[{"type":"module","name":"com.specificlanguages.json","moduleKind":"language","reference":"f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"}]}}""",
            ),
        )
    }

    @Test
    fun `find-usages request and response JSON carry usage results`() {
        val target = NodeTarget.NodeReference(
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
        )
        val request = FindUsagesRequest(token = "secret", target = target, limit = 100)
        val serializedRequest = ProtocolJson.encodeRequest(request)

        assertContains(serializedRequest, """"type":"find-usages"""")
        assertContains(serializedRequest, """"limit":100""")
        assertEquals(
            request,
            ProtocolJson.decodeRequest(serializedRequest),
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
            ProtocolJson.decodeResponse(
                """{"type":"usages","limit":100,"truncated":false,"usages":[{"role":"concept","owner":{"type":"node","name":"JsonObject","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","reference":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905"}}]}""",
            ),
        )
    }

    @Test
    fun `find-instances request and nodes response JSON carry node results`() {
        val request = FindInstancesRequest(
            token = "secret",
            concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
            exact = true,
            limit = 100,
        )
        val serializedRequest = ProtocolJson.encodeRequest(request)

        assertContains(serializedRequest, """"type":"find-instances"""")
        assertContains(serializedRequest, """"concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration"""")
        assertContains(serializedRequest, """"exact":true""")
        assertContains(serializedRequest, """"limit":100""")
        assertEquals(
            request,
            ProtocolJson.decodeRequest(serializedRequest),
        )

        val response = FindInstancesResponse(
            limit = 100,
            truncated = false,
            nodes = listOf(
                MpsNodeSummaryJson(
                    type = "root",
                    name = "JsonObject",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
                ),
            ),
        )
        val serializedResponse = ProtocolJson.encodeResponse(response)

        assertContains(serializedResponse, """"type":"nodes"""")
        assertFalse(serializedResponse.contains(""""id"""), "node summary must omit model-local id: $serializedResponse")
        assertFalse(serializedResponse.contains(""""model"""), "node summary must omit separate model: $serializedResponse")
        assertEquals(
            response,
            ProtocolJson.decodeResponse(serializedResponse),
        )
    }

    @Test
    fun `decoding a request without a type discriminator fails`() {
        assertFailsWith<SerializationException> {
            ProtocolJson.decodeRequest("""{"token":"secret"}""")
        }
    }

    @Test
    fun `decoding a request that omits a required non-null field is rejected`() {
        // Gson's reflective adapter left modelTarget null here; kotlinx.serialization rejects the missing field.
        val exception = assertFailsWith<SerializationException> {
            ProtocolJson.decodeRequest("""{"type":"model-resave","token":"secret"}""")
        }
        assertContains(exception.message ?: "", "modelTarget")
    }

    @Test
    fun `list request target stays optional`() {
        assertEquals(
            MpsListRequest(token = "secret", target = null, depth = 1),
            ProtocolJson.decodeRequest("""{"type":"list","token":"secret","depth":1}"""),
        )
    }
}
