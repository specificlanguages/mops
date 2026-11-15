package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NodeFeatureLookupTest {
    private data class Feature(val name: String, val owner: String)

    private fun find(kind: NodeFeatureKind, name: String, vararg features: Feature): Feature =
        requireNamedFeature(kind, "a.b.Concept", name, features.toList()) { it.name to it.owner }

    @Test
    fun `finds the one feature with the name`() {
        val wanted = Feature("name", "a.b.Named")
        assertEquals(wanted, find(NodeFeatureKind.PROPERTY, "name", Feature("other", "a.b.Concept"), wanted))
    }

    @Test
    fun `a missing feature fails and lists the known names sorted`() {
        val failure = assertFailsWith<NodeFeatureLookupException> {
            find(NodeFeatureKind.REFERENCE_ROLE, "target", Feature("zeta", "a.b.Concept"), Feature("alpha", "a.b.Base"))
        }
        assertEquals(MpsErrorCode.ROLE_NOT_FOUND, failure.code)
        assertEquals("concept a.b.Concept has no reference role 'target'; its reference roles: alpha, zeta", failure.message)
    }

    @Test
    fun `a missing feature on a concept without features says none`() {
        val failure = assertFailsWith<NodeFeatureLookupException> { find(NodeFeatureKind.CHILD_ROLE, "body") }
        assertEquals("concept a.b.Concept has no child role 'body'; its child roles: none", failure.message)
    }

    @Test
    fun `a name that several features share fails and names the declaring concepts`() {
        val failure = assertFailsWith<NodeFeatureLookupException> {
            find(NodeFeatureKind.PROPERTY, "name", Feature("name", "a.b.Second"), Feature("name", "a.b.First"))
        }
        assertEquals(MpsErrorCode.AMBIGUOUS_PROPERTY, failure.code)
        assertEquals(
            "concept a.b.Concept has 2 properties named 'name', declared by a.b.First, a.b.Second",
            failure.message,
        )
    }

    @Test
    fun `ambiguous roles use the role error code`() {
        val failure = assertFailsWith<NodeFeatureLookupException> {
            find(NodeFeatureKind.CHILD_ROLE, "body", Feature("body", "a.b.X"), Feature("body", "a.b.Y"))
        }
        assertEquals(MpsErrorCode.AMBIGUOUS_ROLE, failure.code)
    }
}
