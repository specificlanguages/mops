package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.*

class ModelCreationSemanticsTest {
    @Test
    fun `code mode retains a native module and returns a native model from a command block`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform).execute(CodeRunRequest(
                "", """
                    def owner = project.read { mops.lookup.requireModule('com.specificlanguages.json') }
                    project.command {
                      def model = owner.createModel('.fromCode')
                      [name: model.name.value, owner: model.module.moduleName, model: model]
                    }
                """.trimIndent(), "model-creation.groovy",
            ))

            assertContains(requireNotNull(response.output), "\"name\":\"com.specificlanguages.json.fromCode\"")
            assertContains(response.output!!, "\"owner\":\"com.specificlanguages.json\"")
            Unit
        }
    }

    @Test
    fun `single-file creation expands relative name saves and reports identity`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val module = project.projectModulesWithGenerators.first { it.moduleName == "com.specificlanguages.json" }
            val response = WriteTransaction().run(project) {
                ModelCreator(project).create(CreateModelRequest("", ".created", module.moduleReference.toString()))
            }

            val report = assertNotNull(response.report)
            assertEquals("com.specificlanguages.json.created", report.modelName)
            assertEquals(ModelPersistence.SINGLE_FILE, report.persistence)
            assertTrue(report.location.endsWith(".mps"), report.location)
            assertTrue(java.nio.file.Path.of(report.location).exists())
            project.modelAccess.computeReadAction {
                assertNotNull(org.jetbrains.mps.openapi.persistence.PersistenceFacade.getInstance()
                    .createModelReference(report.modelReference).resolve(project.repository))
            }
            Unit
        }
    }

    @Test
    fun `file-per-root dry run plans without creating repository state or files`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val module = project.projectModulesWithGenerators.first { it.moduleName == "com.specificlanguages.json" }
            val response = project.modelAccess.computeReadAction {
                ModelCreator(project).create(CreateModelRequest("", ".planned", module.moduleName!!, filePerRoot = true, dryRun = true))
            }

            val plan = assertNotNull(response.plan)
            assertEquals(ModelPersistence.FILE_PER_ROOT, plan.persistence)
            assertFalse(java.nio.file.Path.of(plan.location).exists())
            project.modelAccess.computeReadAction { assertTrue(module.models.none { it.modelName == plan.modelName }) }
            Unit
        }
    }
    @Test
    fun `an aspect model of a language gets the devkit MPS gives that aspect`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val handler = DomainRequestHandler(projectPath, SharedMpsEnvironment.mpsAccess(project))
            handler.handleDomainRequest(CreateLanguageRequest("", "example.lang"))
            val report = assertNotNull(assertIs<ModelCreationResponse>(
                handler.handleDomainRequest(CreateModelRequest("", "example.lang.structure", "example.lang")),
            ).report)

            assertEquals(listOf("jetbrains.mps.devkit.aspect.structure"), usedDevkits(project, report.modelReference))
            assertContains(java.nio.file.Path.of(report.location).readText(), "jetbrains.mps.devkit.aspect.structure")
        }
    }

    @Test
    fun `code mode creates an aspect model with the devkit MPS gives that aspect`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val response = CodeModeExecutor(SharedMpsEnvironment.mpsAccess(project), project, SharedMpsEnvironment.platform).execute(CodeRunRequest(
                "", """
                    def owner = project.read { mops.lookup.requireModule('com.specificlanguages.json') }
                    project.command { owner.createModel('.dataFlow').reference.toString() }
                """.trimIndent(), "aspect-model-creation.groovy",
            ))

            val modelReference = requireNotNull(response.output).trim().removeSurrounding("\"")
            assertEquals(listOf("jetbrains.mps.devkit.aspect.dataflow"), usedDevkits(project, modelReference))
        }
    }

    private fun usedDevkits(project: jetbrains.mps.project.Project, modelReference: String): List<String> =
        project.modelAccess.computeReadAction {
            val model = org.jetbrains.mps.openapi.persistence.PersistenceFacade.getInstance()
                .createModelReference(modelReference).resolve(project.repository)
            (model as jetbrains.mps.smodel.SModelInternal).importedDevkits().map { it.moduleName!! }
        }
}
