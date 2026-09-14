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
                        def model = project.model('com.specificlanguages.json.structure')
                        def node = model.createNode(project.concept('jetbrains.mps.baseLanguage.ClassConcept'))
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
                        def concept = project.concept('jetbrains.mps.lang.structure.ConceptDeclaration')
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
                        mops.search.eachInstanceOf(project.concept('jetbrains.mps.lang.structure.ConceptDeclaration'), project.scope) {
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
}
