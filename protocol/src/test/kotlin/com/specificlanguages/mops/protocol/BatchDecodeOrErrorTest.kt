package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BatchDecodeOrErrorTest {
    private fun failure(text: String): BatchDecodeResult.Failure {
        val result = ProtocolJson.decodeBatchOrError(text)
        assertTrue(result is BatchDecodeResult.Failure, "expected failure, got $result")
        return result
    }

    @Test
    fun `valid batch decodes to success`() {
        val text = """{"operations":[{"op":"delete","target":"model/1"}]}"""
        val result = ProtocolJson.decodeBatchOrError(text)
        assertTrue(result is BatchDecodeResult.Success)
        assertEquals(1, result.batch.operations.size)
    }

    @Test
    fun `top-level not an object is batch-shape`() {
        val f = failure("""[1,2,3]""")
        assertEquals(BatchDecodeErrorCategory.BatchShape, f.category)
        assertEquals(null, f.operationIndex)
        assertEquals(
            """edit batch must be a JSON object with an "operations" array — see: mops explain edit""",
            f.detail,
        )
    }

    @Test
    fun `missing operations array is batch-shape`() {
        val f = failure("""{"ops":[]}""")
        assertEquals(BatchDecodeErrorCategory.BatchShape, f.category)
    }

    @Test
    fun `operations not an array is batch-shape`() {
        val f = failure("""{"operations":{}}""")
        assertEquals(BatchDecodeErrorCategory.BatchShape, f.category)
    }

    @Test
    fun `malformed json is batch-shape`() {
        val f = failure("""{"operations":[""")
        assertEquals(BatchDecodeErrorCategory.BatchShape, f.category)
    }

    @Test
    fun `unknown op reports index derived list and did-you-mean`() {
        val f = failure("""{"operations":[{"op":"delete","target":"m/1"},{"op":"x"},{"op":"addNode","target":"m/1"}]}""")
        assertEquals(BatchDecodeErrorCategory.UnknownOp, f.category)
        assertEquals(1, f.operationIndex)
        assertEquals("x", f.opKind)
        assertTrue("supported: ${EditNotation.operationNames.joinToString(", ")}" in f.detail)
        assertTrue("See: mops explain edit" in f.detail)
    }

    @Test
    fun `unknown op close to a supported op suggests it`() {
        val f = failure("""{"operations":[{"op":"addNode","target":"m/1"}]}""")
        val supported = EditNotation.operationNames.joinToString(", ")
        assertEquals(
            """operations[0]: unknown op "addNode" — supported: $supported. Did you mean "addRoot"? See: mops explain edit""",
            f.detail,
        )
    }

    @Test
    fun `unknown op far from any supported op omits did-you-mean`() {
        val f = failure("""{"operations":[{"op":"zzzzzzzz","target":"m/1"}]}""")
        assertTrue("Did you mean" !in f.detail, f.detail)
    }

    @Test
    fun `missing op is unknown op tier`() {
        val f = failure("""{"operations":[{"target":"m/1"}]}""")
        assertEquals(BatchDecodeErrorCategory.UnknownOp, f.category)
        assertEquals(0, f.operationIndex)
    }

    @Test
    fun `missing required field points at op page`() {
        val f = failure("""{"operations":[{"op":"copyNode","target":"m/1","source":"m/2"}]}""")
        assertEquals(BatchDecodeErrorCategory.MissingField, f.category)
        assertEquals(
            """operations[0]: copyNode requires "role" — see: mops explain edit.copyNode""",
            f.detail,
        )
    }

    @Test
    fun `unknown field points at op page`() {
        val f = failure("""{"operations":[{"op":"copyNode","target":"m/1","source":"m/2","role":"r","roel":"x"}]}""")
        assertEquals(BatchDecodeErrorCategory.UnknownField, f.category)
        assertEquals(
            """operations[0]: copyNode has unknown field "roel" — see: mops explain edit.copyNode""",
            f.detail,
        )
    }

    @Test
    fun `invalid target points at target page`() {
        val f = failure("""{"operations":[{"op":"moveNode","target":"m/1","into":{"bogus":true},"role":"r"}]}""")
        assertEquals(BatchDecodeErrorCategory.InvalidTarget, f.category)
        assertEquals(
            """operations[0]: moveNode field "into" is not a valid target — see: mops explain target""",
            f.detail,
        )
    }

    @Test
    fun `invalid position points at position page`() {
        val f = failure("""{"operations":[{"op":"addChild","target":"m/1","role":"r","concept":"c","position":"middle"}]}""")
        assertEquals(BatchDecodeErrorCategory.InvalidPosition, f.category)
        assertEquals(
            """operations[0]: addChild field "position" is not a valid position — see: mops explain position""",
            f.detail,
        )
    }

    @Test
    fun `nullable reference target may be null`() {
        val f = ProtocolJson.decodeBatchOrError(
            """{"operations":[{"op":"setReference","target":"m/1","role":"r","to":null}]}""",
        )
        assertTrue(f is BatchDecodeResult.Success, "expected success, got $f")
    }

    @Test
    fun `wrong-typed primitive field is reported`() {
        val f = failure("""{"operations":[{"op":"setProperty","target":"m/1","name":123}]}""")
        assertEquals(BatchDecodeErrorCategory.WrongType, f.category)
        assertEquals(0, f.operationIndex)
    }

    @Test
    fun `supported list tracks EditNotation operation names`() {
        val f = failure("""{"operations":[{"op":"nope","target":"m/1"}]}""")
        assertTrue(EditNotation.operationNames.joinToString(", ") in f.detail)
    }
}
