package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeLookupTest {
    @Test
    fun `lookup distinguishes missing identities from required objects and invalid requests`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def fails = { Class type, Closure action ->
                        try { action(); assert false: 'Expected ' + type.name }
                        catch (Exception failure) { assert type.isInstance(failure): failure }
                    }
                    fails(IllegalStateException) { mops.lookup.module('missing') }
                    project.read {
                        assert help(mops.lookup).contains('mops.lookup.node')
                        assert mops.lookup.module('missing') == null
                        assert mops.lookup.model('missing') == null
                        assert mops.lookup.conceptByName('jetbrains.mps.baseLanguage.NoSuchMopsConcept') == null
                        fails(IllegalArgumentException) { mops.lookup.requireModule('missing') }
                        fails(IllegalArgumentException) { mops.lookup.requireModel('missing') }
                        fails(com.specificlanguages.mops.daemon.core.MpsRequestException) {
                            mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.NoSuchMopsConcept')
                        }
                        fails(IllegalArgumentException) { mops.lookup.node('invalid') }
                        fails(IllegalArgumentException) { mops.lookup.conceptByName('') }
                        try {
                            mops.lookup.conceptByName('Expression')
                            assert false: 'Expected untrusted runtime error'
                        } catch (com.specificlanguages.mops.daemon.core.MpsRequestException failure) {
                            assert failure.code.name() == 'LANGUAGE_NOT_LOADED'
                        }
                        def module = mops.lookup.requireModule('com.specificlanguages.json')
                        assert mops.lookup.module(module.moduleName).is(module)
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        assert mops.lookup.model(model.reference.toString()).is(model)
                        def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept')
                        assert mops.lookup.conceptByName('jetbrains.mps.baseLanguage.ClassConcept') == concept
                    }
                    def reference = project.command {
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        def concept = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept')
                        def node = model.createNode(concept)
                        model.addRootNode(node)
                        node.reference.toString()
                    }
                    project.read { assert mops.lookup.requireNode(reference) != null }
                    project.command { mops.lookup.requireNode(reference).delete() }
                    project.read {
                        assert mops.lookup.node(reference) == null
                        fails(IllegalArgumentException) { mops.lookup.requireNode(reference) }
                    }
                    return 'ok'
                """.trimIndent(), "lookup.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
