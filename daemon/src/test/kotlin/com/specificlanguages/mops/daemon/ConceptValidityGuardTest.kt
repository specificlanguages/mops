package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertEquals

class ConceptValidityGuardTest {
    @Test
    fun `derives the probable owning language from a persisted structure name`() {
        assertEquals(
            "no valid MPS Concept resolved for \"org.iets3.core.expr.base.structure.LogicalIffExpression\" " +
                "(probable owning language: org.iets3.core.expr.base) — either the name is wrong, or its owning " +
                "language is not compiled or not loaded into the project; build the language and retry",
            ConceptValidityGuard.messageForUnresolvedConcept(
                "org.iets3.core.expr.base.structure.LogicalIffExpression",
            ),
        )
    }

    @Test
    fun `omits the language context when the name has no structure infix`() {
        assertEquals(
            "no valid MPS Concept resolved for \"NotAQualifiedName\" — either the name is wrong, or its owning " +
                "language is not compiled or not loaded into the project; build the language and retry",
            ConceptValidityGuard.messageForUnresolvedConcept("NotAQualifiedName"),
        )
    }
}
