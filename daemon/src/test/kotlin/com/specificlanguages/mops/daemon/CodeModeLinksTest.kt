package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeModeLinksTest {
    @Test
    fun `indexed children and references read and write native links`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project).execute(
                CodeRunRequest("", """
                    import org.jetbrains.mps.openapi.model.SReference
                    def fails = { Class type, Closure action ->
                        try { action(); assert false: 'Expected ' + type.name }
                        catch (Exception failure) { assert type.isInstance(failure): failure }
                    }
                    def node = project.read {
                        project.model('com.specificlanguages.json.structure').rootNodes.find {
                            it.properties['name'] == 'JsonFile'
                        }
                    }
                    assert node != null
                    def child = node.child
                    def children = node.children
                    def references = node.references
                    fails(IllegalStateException) { child['implements'] }
                    fails(IllegalStateException) { children['implements'] }
                    fails(IllegalStateException) { references['extends'] }
                    fails(IllegalStateException) { child['implements'] = null }
                    fails(IllegalStateException) { children['implements'] = [] }
                    fails(IllegalStateException) { references['extends'] = null }
                    project.read {
                        assert child['missing'] == null
                        assert children['missing'] == []
                        assert references['missing'] == null
                        assert child['implements'].is(children['implements'][0])
                        assert references['extends'] instanceof SReference
                        assert references['extends'].targetNode != null
                        fails(IllegalStateException) { child['implements'] = null }
                        fails(IllegalStateException) { children['implements'] = [] }
                        fails(IllegalStateException) { references['extends'] = null }
                    }
                    project.command {
                        def role = 'propertyDeclaration'
                        def link = node.concept.containmentLinks.find { it.name == role }
                        def concept = project.concept('jetbrains.mps.lang.structure.PropertyDeclaration')
                        def a = node.model.createNode(concept)
                        def b = node.model.createNode(concept)
                        assert children[role] == []
                        assert child[role] == null
                        child[role] = a
                        assert child[role].is(a)
                        assert children[role] == [a]
                        children[role] = [a, b]
                        assert node.getChildren(link).toList() == [a, b]
                        fails(IllegalStateException) { child[role] }
                        children[role] = [b, a]
                        assert children[role] == [b, a]
                        fails(IllegalArgumentException) { children[role] = [a, a] }
                        fails(IllegalArgumentException) { children[role] = [node] }
                        fails(IllegalArgumentException) { children[role] = ['invalid'] }
                        assert children[role] == [b, a]
                        child[role] = a
                        assert children[role] == [a]
                        assert b.parent == null
                        child[role] = null
                        assert children[role] == []
                        assert a.parent == null
                        children[role] = [a, b]
                        children[role] = []
                        assert child[role] == null
                        def statement = node.model.createNode(project.concept('jetbrains.mps.baseLanguage.IfStatement'))
                        def singleLink = statement.concept.containmentLinks.find { !it.multiple }
                        assert singleLink != null
                        statement.children[singleLink.name] = [a, b]
                        assert statement.children[singleLink.name] == [a, b]
                        fails(IllegalStateException) { statement.child[singleLink.name] }
                        statement.children[singleLink.name] = []
                        fails(IllegalArgumentException) { child['missing'] = a }
                        fails(IllegalArgumentException) { children['missing'] = [] }

                        def original = references['extends']
                        def target = original.targetNode
                        references['extends'] = null
                        assert references['extends'] == null
                        references['extends'] = target
                        assert references['extends'].targetNode.is(target)
                        references['extends'] = target.reference
                        assert references['extends'].targetNodeReference == target.reference
                        references['extends'] = original
                        assert references['extends'].targetNode.is(target)
                        assert references['extends'].sourceNode.is(node)
                        def other = node.model.createNode(node.concept)
                        other.references['extends'] = original
                        assert other.references['extends'].sourceNode.is(other)
                        assert other.references['extends'].targetNode.is(target)
                        fails(IllegalArgumentException) { references['missing'] = target }
                        fails(IllegalArgumentException) { references['extends'] = 'invalid' }
                        assert references['extends'].targetNode.is(target)
                    }
                    return 'ok'
                """.trimIndent(), "indexed-links.groovy"),
            )
            assertEquals("ok", response.output)
        }
    }
}
