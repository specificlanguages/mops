package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NodeIdSpellingTest {

    // Touching the shared environment boots the MPS platform, so PersistenceFacade.getInstance() — which the id parser
    // and IdEncoder rely on — is registered.
    private val persistence: PersistenceFacade =
        SharedMpsEnvironment.sharedMpsAccess.read { PersistenceFacade.getInstance() }

    @Test
    fun `reads the decimal and encoded spellings as the same id`() {
        val fromDecimal = assertNotNull(parseNodeIdOrNull(persistence, DECIMAL_ID))
        val fromEncoded = assertNotNull(parseNodeIdOrNull(persistence, ENCODED_ID))

        assertEquals(fromDecimal, fromEncoded)
        assertEquals(DECIMAL_ID, persistence.asString(fromEncoded))
    }

    @Test
    fun `reads an all-digit id as decimal rather than encoded`() {
        assertEquals(persistence.createNodeId("123456"), parseNodeIdOrNull(persistence, "123456"))
    }

    @Test
    fun `returns null for a malformed id`() {
        assertNull(parseNodeIdOrNull(persistence, "not a node id"))
    }

    @Test
    fun `normalizes an encoded reference id to the decimal spelling`() {
        assertEquals(
            "$MODEL_REFERENCE/$DECIMAL_ID",
            normalizeNodeReferenceSpelling(persistence, "$MODEL_REFERENCE/$ENCODED_ID"),
        )
    }

    @Test
    fun `leaves a decimal reference unchanged`() {
        val decimal = "$MODEL_REFERENCE/$DECIMAL_ID"
        assertEquals(decimal, normalizeNodeReferenceSpelling(persistence, decimal))
    }

    @Test
    fun `leaves a reference whose id parses as neither spelling unchanged`() {
        val malformed = "$MODEL_REFERENCE/not-an-id"
        assertEquals(malformed, normalizeNodeReferenceSpelling(persistence, malformed))
    }

    @Test
    fun `leaves a string without an id part unchanged`() {
        assertEquals(MODEL_REFERENCE, normalizeNodeReferenceSpelling(persistence, MODEL_REFERENCE))
    }

    private companion object {
        const val MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val DECIMAL_ID = "2110045694544566904"
        const val ENCODED_ID = "1P8oQ4NaXDS"
    }
}
