package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeLookupTest {
    @Test
    fun `lookup distinguishes missing identities from required objects and invalid requests`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform).execute(
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

    @Test
    fun `node lookup accepts the node target notations of the CLI in either node ID spelling`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def fails = { Class type, Closure action ->
                        try { action(); assert false: 'Expected ' + type.name }
                        catch (Exception failure) { assert type.isInstance(failure): failure }
                    }
                    def node = project.command {
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        def created = model.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'))
                        model.addRootNode(created)
                        created
                    }
                    project.read {
                        def modelName = node.model.name.value
                        def modelReference = node.model.reference.toString()
                        def decimalId = node.nodeId.toString()
                        def encodedId = new jetbrains.mps.smodel.persistence.def.v9.IdEncoder().toText(node.nodeId)
                        assert encodedId != decimalId
                        [
                            "${'$'}modelName/${'$'}decimalId",
                            "${'$'}modelName/${'$'}encodedId",
                            "${'$'}modelReference/${'$'}decimalId",
                            "${'$'}modelReference/${'$'}encodedId",
                            // A model reference with a stale name part matches no model target, so it resolves as a
                            // serialized node reference, by model ID.
                            "${'$'}{modelReference.replaceFirst(/\(.*\)${'$'}/, '(stale.name)')}/${'$'}encodedId",
                        ].each { target ->
                            assert mops.lookup.requireNode(target).is(node): target
                            assert mops.lookup.node(target).is(node): target
                        }
                        assert mops.lookup.node("${'$'}modelName/1") == null
                        fails(IllegalArgumentException) { mops.lookup.node('invalid') }
                        try {
                            mops.lookup.requireNode("${'$'}modelName/1")
                            assert false: 'Expected a miss'
                        } catch (IllegalArgumentException failure) {
                            assert failure.message.contains('<model>/<node ID>'): failure.message
                            assert failure.message.contains('serialized node reference'): failure.message
                        }
                    }
                    return 'ok'
                """.trimIndent(), "lookup-node-notations.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
