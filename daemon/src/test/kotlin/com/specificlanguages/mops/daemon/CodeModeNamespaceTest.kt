package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeNamespaceTest {
    @Test
    fun `nested extension getter and method resolve in a Code Mode shell`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    assert mops.is(mops)
                    assert mops.parsing.is(mops.parsing)
                    assert mops.search.is(mops.search)
                    assert mops.lookup.is(mops.lookup)
                    assert mops.editing.is(mops.editing)
                    assert mops.editing.build.class.name == 'com.specificlanguages.mops.daemon.MopsEditingBuild'
                    assert mops.project.is(project)
                    assert mops.access.class.name == 'com.specificlanguages.mops.daemon.JetBrainsMpsAccess'
                    assert mops.parsing.java.class.name == 'com.specificlanguages.mops.daemon.JavaSnippetParser'
                    return project.read {
                        def count = 0
                        def concept = mops.lookup.requireConceptByName('jetbrains.mps.lang.structure.ConceptDeclaration')
                        mops.search.eachInstanceOf(concept, project.scope) { count++ }
                        assert count > 0
                        'ok'
                    }
                """.trimIndent(), "mops-namespace.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
