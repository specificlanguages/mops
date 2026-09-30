package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeTraversalTest {
    @Test
    fun `Groovy containment navigation preserves order excludes self and requires read access`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def fails = { Class type, Closure action ->
                        try { action(); assert false: 'Expected ' + type.name }
                        catch (Exception failure) { assert type.isInstance(failure): failure }
                    }
                    def nodes = project.command {
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        def concept = mops.lookup.requireConceptByName('jetbrains.mps.lang.structure.ConceptDeclaration')
                        def nodes = (0..<5).collect { model.createNode(concept) }
                        def (root, first, grandchild, second, target) = nodes
                        model.addRootNode(root)
                        model.addRootNode(target)
                        root.children['propertyDeclaration'] = [first, second]
                        first.child['propertyDeclaration'] = grandchild
                        root.references['extends'] = target
                        assert root.descendants == [first, grandchild, second]
                        assert grandchild.ancestors == [first, root]
                        assert root.ancestors == []
                        assert second.descendants == []
                        assert target.descendants == []
                        assert target.ancestors == []
                        nodes
                    }
                    def (root, first, grandchild, second, target) = nodes
                    fails(IllegalStateException) { root.descendants }
                    fails(IllegalStateException) { grandchild.ancestors }
                    def descendants = project.read {
                        assert root.descendants.findAll { it.parent.is(root) } == [first, second]
                        assert grandchild.ancestors.find { it.parent == null }.is(root)
                        fails(UnsupportedOperationException) { root.descendants.clear() }
                        fails(UnsupportedOperationException) { grandchild.ancestors.add(target) }
                        root.descendants
                    }
                    project.command { first.child['propertyDeclaration'] = null }
                    project.read {
                        assert descendants == [first, grandchild, second]
                        assert root.descendants == [first, second]
                        assert grandchild.ancestors == []
                    }
                    return 'ok'
                """.trimIndent(), "containment-navigation.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
