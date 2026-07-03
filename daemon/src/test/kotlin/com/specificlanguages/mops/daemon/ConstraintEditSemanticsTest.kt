package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConstraintEditSemanticsTest {

    @Test
    fun `a disallowed child concept is blocked by default and reported`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = LINK_DECLARATION,
                        ),
                    ),
                )
            }

            assertTrue(response.violations.any { it.constraint == "containment" && it.operation == 0 })
            assertTrue(response.created.isEmpty())
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertTrue(childrenInRole(jsonFile, "propertyDeclaration").isEmpty())
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `no-constraints applies a disallowed child and still reports the violation`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = LINK_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "role", value = "forced")),
                        ),
                    ),
                    force = true,
                )
            }

            assertTrue(response.violations.any { it.constraint == "containment" })
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val added = childrenInRole(jsonFile, "propertyDeclaration").single()
            assertEquals(LINK_DECLARATION, added.concept)
            assertTrue(structureModel(projectPath).readText().contains("""value="forced""""))
        }
    }

    @Test
    fun `exceeding a single-valued role is blocked by default and reported`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "helpURL",
                            concept = HELP_URL,
                        ),
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "helpURL",
                            concept = HELP_URL,
                        ),
                    ),
                )
            }

            assertTrue(response.violations.any { it.constraint == "cardinality" })
            assertTrue(response.created.isEmpty())
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertTrue(childrenInRole(jsonFile, "helpURL").isEmpty())
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `a batch with no violations reports an empty list and applies`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = PROPERTY_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "ok")),
                        ),
                    ),
                )
            }

            assertTrue(response.violations.isEmpty())
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertEquals("ok", propertyValueOrNull(childrenInRole(jsonFile, "propertyDeclaration").single(), "name"))
            assertTrue(structureModel(projectPath).readText().contains("""value="ok""""))
        }
    }

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun structureModel(projectPath: Path): Path = projectPath.resolve(STRUCTURE_MODEL_PATH)

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"

        const val PROPERTY_DECLARATION = "jetbrains.mps.lang.structure.structure.PropertyDeclaration"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
        const val HELP_URL = "jetbrains.mps.lang.resources.structure.HelpURL"

        const val JSON_FILE_REF = "$STRUCTURE_MODEL/2110045694544566904"
    }
}
