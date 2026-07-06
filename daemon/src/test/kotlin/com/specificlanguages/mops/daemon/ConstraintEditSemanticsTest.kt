package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
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
    fun `advisory applies a disallowed child and still reports the violation`() {
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
                    constraints = ConstraintEnforcement.ADVISORY,
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

    @Test
    fun `a reference outside its language-defined scope is blocked by default and reported`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            // A concept cannot extend itself: the structure language's `extends` scope excludes the concept (and its
            // subconcepts) to prevent inheritance cycles, so this self-reference is out of scope.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "extends",
                            to = EditTarget.NodeReference(JSON_FILE_REF),
                        ),
                    ),
                )
            }

            assertTrue(response.violations.any { it.constraint == "referenceScope" && it.operation == 0 })
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `advisory applies an out-of-scope reference and still reports the violation`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "extends",
                            to = EditTarget.NodeReference(JSON_FILE_REF),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            assertTrue(response.violations.any { it.constraint == "referenceScope" })
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val extendsReference = jsonFile.references.orEmpty().single { it.role == "extends" }
            assertEquals("JsonFile", extendsReference.target.name)
        }
    }

    @Test
    fun `a reference within its language-defined scope reports no scope violation`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            // A new concept may extend JsonFile: it is a visible concept and not an ancestor of the new one, so it is
            // inside the `extends` scope. This exercises the passing side of the scope membership check.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "ExtendsInScope")),
                            references = listOf(
                                MpsNodeReferenceJson(
                                    role = "extends",
                                    target = MpsNodeReferenceTargetJson(
                                        model = STRUCTURE_MODEL,
                                        node = JSON_FILE_NODE_ID,
                                    ),
                                ),
                            ),
                            alias = "new",
                        ),
                    ),
                )
            }

            assertTrue(response.violations.none { it.constraint == "referenceScope" })
            assertTrue(response.created.containsKey("new"))
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
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val HELP_URL = "jetbrains.mps.lang.resources.structure.HelpURL"

        const val JSON_FILE_NODE_ID = "2110045694544566904"
        const val JSON_FILE_REF = "$STRUCTURE_MODEL/$JSON_FILE_NODE_ID"
    }
}
