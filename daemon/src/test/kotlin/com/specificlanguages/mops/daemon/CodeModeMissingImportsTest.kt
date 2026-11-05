package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.CodeResultResponse
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.FindingSeverity
import jetbrains.mps.project.MPSProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodeModeMissingImportsTest {
    @Test
    fun `a command adds the used languages and model imports its changed models need and reports them`() {
        SharedMpsEnvironment.withOpenProjectCopy(projectName = SANDBOX) { project, _ ->
            val response = run(project, """
                def owner = project.read { mops.lookup.requireModule('$SANDBOX_MODEL') }
                project.command {
                    def model = owner.createModel('.client')
                    def calculator = mops.lookup.requireModel('$SANDBOX_MODEL').rootNodes.find { it.name == 'Calculator' }
                    def type = model.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassifierType'))
                    type.references['classifier'] = calculator
                    def subclass = model.createNode(mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.ClassConcept'))
                    subclass.properties['name'] = 'Client'
                    subclass.child['superclass'] = type
                    model.addRootNode(subclass)
                }
            """.trimIndent())

            assertEquals(
                listOf(
                    "added used language jetbrains.mps.baseLanguage to model $CLIENT_MODEL",
                    "added model import $SANDBOX_MODEL to model $CLIENT_MODEL",
                ),
                response.notes,
            )
            val findings = SharedMpsEnvironment.mpsAccess(project).read { checkModel(CLIENT_MODEL, limit = 0) }.findings
            val errors = findings.filter { it.severity == FindingSeverity.ERROR }
            assertTrue(errors.isEmpty(), "the changed model must declare what it uses; got $errors")
        }
    }

    @Test
    fun `a command whose models already declare what they use reports nothing`() {
        SharedMpsEnvironment.withOpenProjectCopy(projectName = SANDBOX) { project, _ ->
            val response = run(project, """
                project.command {
                    def calculator = mops.lookup.requireModel('$SANDBOX_MODEL').rootNodes.find { it.name == 'Calculator' }
                    calculator.properties['name'] = 'Renamed'
                }
            """.trimIndent())

            assertEquals(emptyList(), response.notes)
        }
    }

    private fun run(project: MPSProject, source: String): CodeResultResponse =
        CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform)
            .execute(CodeRunRequest("", source, "missing-imports.groovy"))

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "baselanguage.sandbox"
        const val CLIENT_MODEL = "baselanguage.sandbox.client"
    }
}
