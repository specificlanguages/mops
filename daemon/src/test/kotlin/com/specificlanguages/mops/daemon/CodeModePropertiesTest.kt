package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModePropertiesTest {
    @Test
    fun `property indexing invokes MPS computed getter and setter handlers`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    project.command {
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        def node = model.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'))
                        def nonStatic = node.concept.properties.find { it.name == 'nonStatic' }
                        node.properties['isStatic'] = 'false'
                        assert node.getProperty(nonStatic) == 'true'
                        assert node.properties['isStatic'] == null
                        node.properties['isStatic'] = 'true'
                        assert node.properties['isStatic'] == 'true'
                        assert node.getProperty(nonStatic) != 'true'
                        node.setProperty(nonStatic, 'true')
                        assert node.properties['isStatic'] == null
                        return 'ok'
                    }
                """.trimIndent(), "property-handlers.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }

    @Test
    fun `Groovy property indexing reads inherited names from native nodes`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    project.read {
                        def concept = mops.lookup.requireConceptByName('jetbrains.mps.lang.structure.ConceptDeclaration')
                        def count = 0
                        mops.search.eachInstanceOf(concept, project.scope) { node ->
                            def descriptor = node.concept.properties.find { it.name == 'name' }
                            assert descriptor != null
                            assert node.properties['name'] == node.getProperty(descriptor)
                            assert node.properties['name'] != null
                            assert node.properties['missingProperty'] == null
                            try {
                                node.properties['name'] = 'changed'
                                assert false: 'Writes must require command access'
                            } catch (IllegalStateException expected) {
                            }
                            count++
                        }
                        assert count > 0
                        return 'ok'
                    }
                """.trimIndent(), "properties.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }

    @Test
    fun `indexed accessor stays live and enforces access on every operation`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def node = project.read {
                        def nodes = []
                        mops.search.eachInstanceOf(mops.lookup.requireConceptByName('jetbrains.mps.lang.structure.ConceptDeclaration'), project.scope) {
                            nodes << it
                        }
                        assert !nodes.empty
                        nodes.first()
                    }
                    def properties = node.properties
                    try {
                        properties['name']
                        assert false: 'Reads must require model access'
                    } catch (IllegalStateException expected) {
                    }
                    try {
                        properties['name'] = 'outside'
                        assert false: 'Writes must require command access'
                    } catch (IllegalStateException expected) {
                    }
                    project.command {
                        def descriptor = node.concept.properties.find { it.name == 'name' }
                        properties['name'] = 'IndexedName'
                        assert node.getProperty(descriptor) == 'IndexedName'
                        node.setProperty(descriptor, 'NativeName')
                        assert properties['name'] == 'NativeName'
                        properties['name'] = null
                        assert node.getProperty(descriptor) == null
                        assert properties['name'] == null
                        properties['name'] = 'SavedName'
                        try {
                            properties['missingProperty'] = 'value'
                            assert false: 'Unknown properties must reject writes'
                        } catch (IllegalArgumentException expected) {
                        }
                    }
                    project.read { assert properties['name'] == 'SavedName' }
                    return 'ok'
                """.trimIndent(), "indexed-properties.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }

    @Test
    fun `properties accessor iterates set property descriptors like the native collection`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    import org.jetbrains.mps.openapi.language.SProperty
                    def fails = { Class type, String fragment, Closure action ->
                        try { action(); assert false: 'Expected ' + type.name }
                        catch (Exception failure) {
                            assert type.isInstance(failure): failure
                            assert failure.message.contains(fragment): failure.message
                        }
                    }
                    def node = project.read {
                        mops.lookup.requireModel('com.specificlanguages.json.structure').rootNodes.find {
                            it.properties['name'] == 'JsonFile'
                        }
                    }
                    fails(IllegalStateException, 'SNode.properties') { node.properties.size() }
                    project.read {
                        def declared = node.concept.properties.collect { it.name }
                        assert node.properties.size() > 0
                        assert node.properties.size() <= declared.size()
                        assert node.properties.every { it instanceof SProperty }
                        assert node.getProperties().collect { it.name }.containsAll(['name', 'conceptId'])
                        assert node.properties.collect { node.properties[it.name] }.every { it != null }
                        assert declared.containsAll(node.properties.collect { it.name })
                        fails(IllegalArgumentException, 'node.concept.properties') { node.properties.name }
                        fails(IllegalArgumentException, 'node.concept.properties') { node.properties.name() }
                        fails(IllegalArgumentException, 'node.concept.properties') { node.properties.name = 'x' }
                    }
                    return 'ok'
                """.trimIndent(), "iterable-properties.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
