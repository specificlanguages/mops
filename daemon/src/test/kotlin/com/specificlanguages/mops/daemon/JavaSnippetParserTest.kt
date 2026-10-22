package com.specificlanguages.mops.daemon

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class JavaSnippetParserTest {
    @Test
    fun `Java parser bridge is loaded by the MOPS IDEA plugin classloader`() {
        SharedMpsEnvironment.platform
        // PluginId.getId is not called from Kotlin here: PluginId became a Kotlin class with a companion object in
        // newer platforms, so a Kotlin call compiled against them fails with NoSuchFieldError on older MPS versions.
        val bridgeClass = JavaParserPluginLoader.loadBridgeMethod().declaringClass

        val pluginClassLoader = assertIs<PluginAwareClassLoader>(bridgeClass.classLoader)
        assertEquals(JavaSnippetParser.PLUGIN_ID, pluginClassLoader.pluginId.idString)
        assertNotEquals(JavaSnippetParser::class.java.classLoader, bridgeClass.classLoader)
    }

    @Test
    fun `Code Mode inserts Java members and statements in a command`() {
        SharedMpsEnvironment.withOpenProjectCopy("base-language-sandbox") { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def parser = mops.parsing.java
                    def model = project.read {
                        mops.lookup.requireModel('baselanguage.sandbox')
                    }
                    def result = project.command {
                        assert parser.class.name == 'com.specificlanguages.mops.daemon.JavaSnippetParser'
                        assert parser.class.methods*.name.contains('addJavaMembersFromString')
                        def parseMethod = parser.class.methods.find { it.name == 'addJavaMembersFromString' && it.parameterCount == 3 }
                        assert parseMethod != null
                        def classes = parser.addJavaClassesFromString(model, 'class Added {}')
                        assert classes.nodes.size() == 1
                        assert classes.nodes[0].name == 'Added'
                        def members = parser.addJavaMembersFromString(classes.nodes[0], 'public int twice(int n) { return n + n; }')
                        assert members.nodes.size() == 1
                        def body = members.nodes[0].child['body']
                        def statements = parser.addJavaStatementsFromString(body, 'return twice(21);')
                        assert statements.nodes.size() == 1
                        [nodes: classes.nodes + members.nodes + statements.nodes, unresolved: statements.unresolved]
                    }
                    return result
                """.trimIndent(), "java-parser.groovy"),
            )
            val output = requireNotNull(response.output)
            assertEquals(0, output.count { it == '\n' })
        }
    }
}
