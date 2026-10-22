package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeSubconceptsTest {
    @Test
    fun `search finds transitive direct and concrete subconcepts and interface implementors`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def expression
                    project.read {
                        assert help(mops.search).contains('mops.search.subConceptsOf')
                        assert !help(mops.lookup).contains('subConceptsOf')
                        expression = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.Expression')
                        def names = { concepts -> concepts.collect { it.qualifiedName } }
                        def all = mops.search.subConceptsOf(expression)
                        assert names(all).contains('jetbrains.mps.baseLanguage.structure.IntegerConstant')
                        assert !all.contains(expression)
                        assert names(all) == names(all).unique().sort()
                        def direct = mops.search.subConceptsOf(expression, false, true)
                        assert names(direct).contains('jetbrains.mps.baseLanguage.structure.BinaryOperation')
                        assert !names(direct).contains('jetbrains.mps.baseLanguage.structure.IntegerConstant')
                        def concrete = mops.search.subConceptsOf(expression, true)
                        assert names(concrete).contains('jetbrains.mps.baseLanguage.structure.IntegerConstant')
                        assert !names(concrete).contains('jetbrains.mps.baseLanguage.structure.BinaryOperation')
                        assert concrete.every { !it.abstract }
                        def named = mops.lookup.requireConceptByName('jetbrains.mps.lang.core.INamedConcept')
                        assert names(mops.search.subConceptsOf(named)).contains('jetbrains.mps.baseLanguage.structure.ClassConcept')
                        assert mops.search.subConceptsOf(expression, true, true).every { !it.abstract && direct.contains(it) }
                    }
                    try {
                        mops.search.subConceptsOf(expression)
                        assert false: 'Expected read access error'
                    } catch (IllegalStateException expected) { }
                    return 'ok'
                """.trimIndent(), "subconcepts.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
