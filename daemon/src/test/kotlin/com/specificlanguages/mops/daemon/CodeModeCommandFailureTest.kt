package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.FindingSeverity
import jetbrains.mps.project.MPSProject
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeModeCommandFailureTest {
    @Test
    fun `a failed command discards its model changes and a later command does not save them`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val structureFile = projectPath.resolve(STRUCTURE_MODEL_PATH)

            val failure = assertFailsWith<IllegalStateException> {
                run(project, """
                    project.command {
                        def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                        model.rootNodes.find { it.properties['name'] == 'JsonFile' }.properties['name'] = 'Broken'
                        throw new IllegalStateException('program failed')
                    }
                """.trimIndent())
            }
            assertContains(failure.message.orEmpty(), "program failed")

            assertEquals("""{"broken":false,"jsonFile":true,"changed":false}""", run(project, """
                project.read {
                    def model = mops.lookup.requireModel('com.specificlanguages.json.structure')
                    def names = model.rootNodes.collect { it.properties['name'] }
                    [broken: names.contains('Broken'), jsonFile: names.contains('JsonFile'), changed: model.changed]
                }
            """.trimIndent()))

            assertEquals("1", run(project, "project.command { 1 }"))
            assertFalse(structureFile.readText().contains("Broken"))
        }
    }

    @Test
    fun `model check after a failed command sees the file content`() {
        SharedMpsEnvironment.withOpenProjectCopy(projectName = SANDBOX) { project, _ ->
            val failure = assertFailsWith<IllegalStateException> {
                run(project, """
                    project.command {
                        def model = mops.lookup.requireModel('$SANDBOX_MODEL')
                        def reference = model.rootNodes.collectMany { it.descendants }
                            .find { it.concept.name == 'VariableReference' }
                        reference.references['variableDeclaration'].targetNode.delete()
                        throw new IllegalStateException('program failed')
                    }
                """.trimIndent())
            }
            assertContains(failure.message.orEmpty(), "program failed")

            val response = SharedMpsEnvironment.mpsAccess(project).read { checkModel(SANDBOX_MODEL, limit = 0) }
            val errors = response.findings.filter { it.severity == FindingSeverity.ERROR }
            assertTrue(errors.isEmpty(), "the failed command's change must not be checked; got $errors")
        }
    }

    private fun run(project: MPSProject, source: String): String? =
        CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform)
            .execute(CodeRunRequest("", source, "command-failure.groovy"))
            .output

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "baselanguage.sandbox"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
