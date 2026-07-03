package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reproduces the "language never compiled" case: a model contains a node whose **MPS Concept** cannot be resolved (its
 * owning language is not on the classpath), so the concept is invalid. Reads must report the node rather than fail;
 * writes follow the [ConstraintEnforcement] policy.
 */
class InvalidConceptSemanticsTest {

    @Test
    fun `get-node reports an unresolved concept instead of failing`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::injectUncompiledConceptNode) { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            val reference = uncompiledNodeReference(project)

            val node = access.read { getNode(NodeTarget.NodeReference(reference)) }

            assertFalse(node.conceptValid)
            assertContains(node.concept, "com.example.uncompiled")
        }
    }

    @Test
    fun `list reports an unresolved concept instead of failing`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::injectUncompiledConceptNode) { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())

            val model = access.read {
                list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"), depth = 1)
            }

            val unresolved = requireNotNull(model.children).single { !it.conceptValid }
            assertContains(unresolved.concept.orEmpty(), "com.example.uncompiled")
        }
    }

    @Test
    fun `strict edit aborts and changes nothing when the target concept did not load`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::injectUncompiledConceptNode) { project, projectPath ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            val reference = uncompiledNodeReference(project)
            val modelFile = projectPath.resolve(STRUCTURE_MODEL_PATH)
            val before = modelFile.readText()

            val exception = assertFailsWith<MpsRequestException> {
                access.write {
                    modelEdit(
                        EditBatch(listOf(EditOperation.Delete(EditTarget.NodeReference(reference)))),
                        constraints = ConstraintEnforcement.STRICT,
                    )
                }
            }

            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, exception.code)
            assertEquals(before, modelFile.readText())
        }
    }

    @Test
    fun `best-effort edit warns about the unloaded language and applies`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::injectUncompiledConceptNode) { project, projectPath ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            val reference = uncompiledNodeReference(project)

            val response = access.write {
                modelEdit(
                    EditBatch(listOf(EditOperation.Delete(EditTarget.NodeReference(reference)))),
                    constraints = ConstraintEnforcement.BEST_EFFORT,
                )
            }

            assertTrue(response.warnings.any { it.contains("com.example.uncompiled") && it.contains("not loaded") })
            // The node was deleted despite its unloaded language: the placeholder concept is gone from the saved model.
            assertFalse(projectPath.resolve(STRUCTURE_MODEL_PATH).readText().contains("com.example.uncompiled"))
        }
    }

    @Test
    fun `a fully compiled project reads without a false positive`() {
        val node = SharedMpsEnvironment.sharedMpsAccess.read {
            getNode(NodeTarget.InModel("com.specificlanguages.json.structure", JSON_FILE_NODE_ID))
        }

        assertTrue(node.conceptValid)
        assertEquals("jetbrains.mps.lang.structure.structure.ConceptDeclaration", node.concept)
    }

    private fun injectUncompiledConceptNode(project: Path) {
        val model = project.resolve(STRUCTURE_MODEL_PATH)
        val text = model.readText()
            .replace(
                "  </registry>",
                """    <language id="cafeba00-0000-4000-8000-000000000001" name="com.example.uncompiled">
      <concept id="8000000000000123456" name="com.example.uncompiled.structure.Widget" flags="ng" index="wDgT0" />
    </language>
  </registry>""",
            )
            .replace(
                "</model>",
                """  <node concept="wDgT0" id="1P8oQ4NbWDgT" />
</model>""",
            )
        model.writeText(text)
    }

    private fun uncompiledNodeReference(project: Project): String =
        project.modelAccess.computeReadAction<String> {
            val module = project.projectModulesWithGenerators.first { it.moduleName == JSON_LANGUAGE_MODULE_NAME }
            val model = module.models.first { it.name.value == STRUCTURE_MODEL_NAME }
            val node = model.rootNodes.first { !it.concept.isValid }
            PersistenceFacade.getInstance().asString(node.reference)
        }

    private companion object {
        const val JSON_FILE_NODE_ID = "2110045694544566904"
        const val JSON_LANGUAGE_MODULE_NAME = "com.specificlanguages.json"
        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
