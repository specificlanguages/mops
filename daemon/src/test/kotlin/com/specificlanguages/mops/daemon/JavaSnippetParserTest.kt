package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaSnippetParserTest {
    @Test
    fun `Code Mode inserts Java members and statements in a command`() {
        SharedMpsEnvironment.withOpenProjectCopy("base-language-sandbox") { project, _ ->
            val response = CodeModeExecutor(JetBrainsMpsAccess(project, DaemonLogger()), project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    def parser = mops.parsing.java
                    def model = project.read {
                        project.model('baselanguage.sandbox')
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
