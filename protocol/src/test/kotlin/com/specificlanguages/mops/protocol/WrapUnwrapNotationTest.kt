package com.specificlanguages.mops.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WrapUnwrapNotationTest {

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

    private fun singleOp(batch: EditBatch): EditOperation = batch.operations.single()

    @Test
    fun `wrap decodes its full field set including inline children and an alias`() {
        val batch = decodeSuccess(
            """{"operations":[{"op":"wrap","target":"m/1","concept":"W","role":"operand","position":"first",
               "children":[{"role":"operand","copy":"m/2"}],"as":"w"}]}""",
        )
        val wrap = assertIs<EditOperation.Wrap>(singleOp(batch))
        assertEquals(EditTarget.NodeReference("m/1"), wrap.target)
        assertEquals("W", wrap.concept)
        assertEquals("operand", wrap.role)
        assertEquals(ChildPosition.First, wrap.position)
        assertEquals("w", wrap.alias)
        assertEquals(EditTarget.NodeReference("m/2"), assertIs<InlineChild.Copy>(wrap.children!!.single()).source)
    }

    @Test
    fun `wrap defaults position to last and carries no alias by default`() {
        val wrap = assertIs<EditOperation.Wrap>(
            singleOp(decodeSuccess("""{"operations":[{"op":"wrap","target":"m/1","concept":"W","role":"r"}]}""")),
        )
        assertEquals(ChildPosition.Last, wrap.position)
        assertNull(wrap.alias)
    }

    @Test
    fun `unwrap decodes a target and a keep descendant target`() {
        val unwrap = assertIs<EditOperation.Unwrap>(
            singleOp(decodeSuccess("""{"operations":[{"op":"unwrap","target":"m/1","keep":{"model":"m","nodeId":"2"}}]}""")),
        )
        assertEquals(EditTarget.NodeReference("m/1"), unwrap.target)
        assertEquals(EditTarget.InModel("m", "2"), unwrap.keep)
    }

    @Test
    fun `wrap requires concept and role`() {
        assertContains(decodeFailure("""{"operations":[{"op":"wrap","target":"m/1","role":"r"}]}"""), "concept")
        assertContains(decodeFailure("""{"operations":[{"op":"wrap","target":"m/1","concept":"W"}]}"""), "role")
    }

    @Test
    fun `unwrap requires keep`() {
        val detail = decodeFailure("""{"operations":[{"op":"unwrap","target":"m/1"}]}""")
        assertContains(detail, "operations[0]")
        assertContains(detail, "unwrap")
        assertContains(detail, "keep")
    }

    @Test
    fun `unwrap takes no alias`() {
        val detail = decodeFailure("""{"operations":[{"op":"unwrap","target":"m/1","keep":"m/2","as":"x"}]}""")
        assertContains(detail, "operations[0]")
        assertContains(detail, "unwrap")
        assertContains(detail, "as")
    }

    @Test
    fun `wrap and unwrap round-trip through encode and decode`() {
        val batch = EditBatch(
            operations = listOf(
                EditOperation.Wrap(
                    target = EditTarget.NodeReference("m/1"),
                    concept = "W",
                    role = "operand",
                    children = listOf(InlineChild.Fresh(role = "operand", concept = "C")),
                    position = ChildPosition.Index(2),
                    alias = "w",
                ),
                EditOperation.Unwrap(target = EditTarget.Alias("\$w"), keep = EditTarget.InModel("m", "3")),
            ),
        )
        assertEquals(batch, ProtocolJson.decodeBatch(ProtocolJson.encodeBatch(batch)))
    }

    @Test
    fun `the schema describes wrap and unwrap operations`() {
        val ops = Json.parseToJsonElement(EditSchema.generate()).jsonObject["properties"]!!.jsonObject["operations"]!!
            .jsonObject["items"]!!.jsonObject["oneOf"]!!.jsonArray.map { it.jsonObject }
        fun op(name: String) = ops.first { it["properties"]!!.jsonObject["op"]!!.jsonObject["const"]!!.jsonPrimitive.content == name }

        val wrap = op("wrap")
        assertContains(wrap["properties"]!!.jsonObject.keys, "concept")
        assertContains(wrap["properties"]!!.jsonObject.keys, "role")
        assertEquals(3, wrap["properties"]!!.jsonObject["children"]!!.jsonObject.let { unwrapNullable(it) }["items"]!!.jsonObject["anyOf"]!!.jsonArray.size)

        val unwrap = op("unwrap")
        assertContains(unwrap["required"]!!.jsonArray.map { it.jsonPrimitive.content }, "keep")
    }

    private fun unwrapNullable(schema: kotlinx.serialization.json.JsonObject): kotlinx.serialization.json.JsonObject {
        val anyOf = schema["anyOf"]?.jsonArray ?: return schema
        return anyOf.map { it.jsonObject }.first { it["type"]?.jsonPrimitive?.content != "null" }
    }
}
