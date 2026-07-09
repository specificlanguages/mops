package com.specificlanguages.mops.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InlineSubtreeNotationTest {

    private fun decode(json: String): BatchDecodeResult = ProtocolJson.decodeBatchOrError(json)

    private fun decodeSuccess(json: String): EditBatch {
        val result = decode(json)
        assertIs<BatchDecodeResult.Success>(result, "expected success, got: $result")
        return result.batch
    }

    private fun decodeFailure(json: String): String {
        val result = decode(json)
        assertIs<BatchDecodeResult.Failure>(result, "expected failure, got: $result")
        return result.detail
    }

    private fun addChild(children: String = "[]", references: String = "[]"): String =
        """{"operations":[{"op":"addChild","target":"m/1","role":"members","concept":"C",
           "children":$children,"references":$references}]}"""

    private fun firstAddChild(batch: EditBatch): EditOperation.AddChild =
        batch.operations.single() as EditOperation.AddChild

    @Test
    fun `a children position accepts a move leaf, a copy leaf, and a fresh spec mixed at any depth`() {
        val batch = decodeSuccess(
            addChild(
                children = """[
                  {"role":"members","move":"m/10"},
                  {"role":"members","copy":{"model":"m","nodeId":"11"}},
                  {"role":"members","concept":"C","children":[{"role":"inner","move":"${'$'}a"}]}
                ]""",
            ),
        )
        val children = firstAddChild(batch).children!!

        val move = assertIs<InlineChild.Move>(children[0])
        assertEquals("members", move.role)
        assertEquals(EditTarget.NodeReference("m/10"), move.source)

        val copy = assertIs<InlineChild.Copy>(children[1])
        assertEquals(EditTarget.InModel("m", "11"), copy.source)

        val fresh = assertIs<InlineChild.Fresh>(children[2])
        val nested = assertIs<InlineChild.Move>(fresh.children!!.single())
        assertEquals(EditTarget.Alias("\$a"), nested.source)
    }

    @Test
    fun `a leaf target accepts a node reference, a model plus nodeId, and an alias`() {
        val batch = decodeSuccess(
            addChild(
                children = """[
                  {"role":"r","move":"model/1"},
                  {"role":"r","move":{"model":"model","nodeId":"2"}},
                  {"role":"r","move":"${'$'}alias"}
                ]""",
            ),
        )
        val sources = firstAddChild(batch).children!!.map { assertIs<InlineChild.Move>(it).source }
        assertEquals(
            listOf(
                EditTarget.NodeReference("model/1"),
                EditTarget.InModel("model", "2"),
                EditTarget.Alias("\$alias"),
            ),
            sources,
        )
    }

    @Test
    fun `a position that mixes a fresh spec with a leaf is rejected with a field-path error`() {
        val detail = decodeFailure(addChild(children = """[{"role":"r","concept":"C","move":"m/1"}]"""))
        assertContains(detail, "operations[0]")
        assertContains(detail, "addChild")
        assertContains(detail, "move")
        assertContains(detail, "concept")
    }

    @Test
    fun `a position that sets both move and copy is rejected`() {
        val detail = decodeFailure(addChild(children = """[{"role":"r","move":"m/1","copy":"m/2"}]"""))
        assertContains(detail, "operations[0]")
        assertContains(detail, "both")
        assertContains(detail, "move")
        assertContains(detail, "copy")
    }

    @Test
    fun `an inline reference accepts the canonical to form with the full edit-target grammar`() {
        val batch = decodeSuccess(addChild(references = """[{"role":"type","to":"other/9"}]"""))
        val reference = firstAddChild(batch).references!!.single()
        assertEquals("type", reference.role)
        assertEquals(EditTarget.NodeReference("other/9"), reference.to)
        assertNull(reference.target)
    }

    @Test
    fun `an inline reference accepts the get-node-shaped target form ignoring enrichment`() {
        val batch = decodeSuccess(
            addChild(
                references = """[{"role":"type","target":{"model":"m","node":"9","name":"N","concept":"c","resolved":true}}]""",
            ),
        )
        val reference = firstAddChild(batch).references!!.single()
        assertNull(reference.to)
        assertEquals(MpsNodeReferenceTargetJson(model = "m", node = "9", name = "N", concept = "c"), reference.target)
    }

    @Test
    fun `an inline reference carrying both to and target is rejected with a field-path error`() {
        val detail = decodeFailure(
            addChild(references = """[{"role":"type","to":"m/1","target":{"model":"m","node":"2"}}]"""),
        )
        assertContains(detail, "operations[0]")
        assertContains(detail, "\"type\"")
        assertContains(detail, "both")
    }

    @Test
    fun `a batch with move, copy, and to-reference round-trips through encode and decode`() {
        val batch = EditBatch(
            operations = listOf(
                EditOperation.AddChild(
                    target = EditTarget.NodeReference("m/1"),
                    role = "members",
                    concept = "C",
                    references = listOf(InlineReference(role = "type", to = EditTarget.NodeReference("m/2"))),
                    children = listOf(
                        InlineChild.Move(role = "members", source = EditTarget.Alias("\$a")),
                        InlineChild.Copy(role = "members", source = EditTarget.InModel("m", "3")),
                        InlineChild.Fresh(role = "members", concept = "D"),
                    ),
                ),
            ),
        )

        val encoded = ProtocolJson.encodeBatch(batch)
        assertEquals(batch, ProtocolJson.decodeBatch(encoded))
    }

    @Test
    fun `the schema describes a child position as a fresh-spec-or-leaf union and a reference as to-or-target`() {
        val schema = Json.parseToJsonElement(EditSchema.generate()).jsonObject
        val addChild = schema["properties"]!!.jsonObject["operations"]!!.jsonObject["items"]!!.jsonObject["oneOf"]!!
            .jsonArray.map { it.jsonObject }
            .first { it["properties"]!!.jsonObject["op"]!!.jsonObject["const"]!!.jsonPrimitive.content == "addChild" }
        val props = addChild["properties"]!!.jsonObject

        val childItems = unwrapNullable(props["children"]!!.jsonObject)["items"]!!.jsonObject
        val branches = childItems["anyOf"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, branches.size, "fresh spec + move leaf + copy leaf")
        assertTrue(branches.any { it["\$ref"] != null }, "fresh spec references a node def")
        assertTrue(branches.any { "move" in (it["properties"]?.jsonObject?.keys ?: emptySet()) }, "a move-leaf branch")
        assertTrue(branches.any { "copy" in (it["properties"]?.jsonObject?.keys ?: emptySet()) }, "a copy-leaf branch")

        val refItems = unwrapNullable(props["references"]!!.jsonObject)["items"]!!.jsonObject
        assertEquals(setOf("role", "to", "target"), refItems["properties"]!!.jsonObject.keys)
    }

    private fun unwrapNullable(schema: JsonObject): JsonObject {
        val anyOf = schema["anyOf"]?.jsonArray ?: return schema
        return anyOf.map { it.jsonObject }.first { it["type"]?.jsonPrimitive?.content != "null" }
    }
}
