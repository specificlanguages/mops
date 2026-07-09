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

class ReplaceNotationTest {

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

    private fun replace(with: String, extra: String = ""): String =
        """{"operations":[{"op":"replace","target":"m/1","with":$with$extra}]}"""

    private fun firstReplace(batch: EditBatch): EditOperation.Replace =
        batch.operations.single() as EditOperation.Replace

    @Test
    fun `with accepts a fresh spec, a move leaf, and a copy leaf`() {
        val fresh = firstReplace(decodeSuccess(replace("""{"concept":"C","properties":[{"name":"n","value":"v"}]}""")))
        val freshWith = assertIs<InlineChild.Fresh>(fresh.with)
        assertEquals("C", freshWith.concept)

        val move = firstReplace(decodeSuccess(replace("""{"move":"m/9"}""")))
        assertEquals(EditTarget.NodeReference("m/9"), assertIs<InlineChild.Move>(move.with).source)

        val copy = firstReplace(decodeSuccess(replace("""{"copy":{"model":"m","nodeId":"9"}}""")))
        assertEquals(EditTarget.InModel("m", "9"), assertIs<InlineChild.Copy>(copy.with).source)
    }

    @Test
    fun `an as alias binds the replacement`() {
        val batch = decodeSuccess(replace("""{"move":"${'$'}src"}""", ""","as":"repl""""))
        assertEquals("repl", firstReplace(batch).alias)
        assertEquals(EditTarget.Alias("\$src"), assertIs<InlineChild.Move>(firstReplace(batch).with).source)
    }

    @Test
    fun `replace requires a with`() {
        val detail = decodeFailure("""{"operations":[{"op":"replace","target":"m/1"}]}""")
        assertContains(detail, "operations[0]")
        assertContains(detail, "replace")
        assertContains(detail, "with")
    }

    @Test
    fun `a with that sets both move and copy is rejected via the shared leaf rule`() {
        val detail = decodeFailure(replace("""{"move":"m/1","copy":"m/2"}"""))
        assertContains(detail, "operations[0]")
        assertContains(detail, "both")
        assertContains(detail, "move")
        assertContains(detail, "copy")
    }

    @Test
    fun `a replace round-trips through encode and decode`() {
        val batch = EditBatch(
            operations = listOf(
                EditOperation.Replace(
                    target = EditTarget.NodeReference("m/1"),
                    with = InlineChild.Fresh(
                        concept = "C",
                        children = listOf(InlineChild.Move(role = "operand", source = EditTarget.Alias("\$a"))),
                    ),
                    alias = "repl",
                ),
            ),
        )
        assertEquals(batch, ProtocolJson.decodeBatch(ProtocolJson.encodeBatch(batch)))
    }

    @Test
    fun `the schema describes replace with a target and a fresh-or-leaf with`() {
        val schema = Json.parseToJsonElement(EditSchema.generate()).jsonObject
        val replace = schema["properties"]!!.jsonObject["operations"]!!.jsonObject["items"]!!.jsonObject["oneOf"]!!
            .jsonArray.map { it.jsonObject }
            .first { it["properties"]!!.jsonObject["op"]!!.jsonObject["const"]!!.jsonPrimitive.content == "replace" }
        val props = replace["properties"]!!.jsonObject

        assertContains(props.keys, "target")
        val branches = props["with"]!!.jsonObject["anyOf"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, branches.size, "fresh spec + move leaf + copy leaf")
        assertContains(replace["required"]!!.jsonArray.map { it.jsonPrimitive.content }, "with")
    }
}
