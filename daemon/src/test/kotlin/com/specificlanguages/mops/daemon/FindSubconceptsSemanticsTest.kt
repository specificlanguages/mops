package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ConceptTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FindSubconceptsSemanticsTest {

    @Test
    fun `reports the resolved concept and the loaded languages it searched`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findSubconcepts("jetbrains.mps.lang.structure.AbstractConceptDeclaration", concreteOnly = false, direct = true)
        }

        assertEquals(ConceptTargetJson("jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"), payload.target)
        assertEquals(SearchScopeJson(SearchScopeKind.LOADED_LANGUAGES), payload.scope)
        assertTrue(
            "jetbrains.mps.lang.structure.structure.ConceptDeclaration" in payload.concepts,
            "ConceptDeclaration extends AbstractConceptDeclaration: ${payload.concepts}",
        )
    }
}
