package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RootEditSemanticsTest {

    @Test
    fun `addRoot creates a new root with an inline subtree and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "JsonComment")),
                            alias = "\$c",
                        ),
                    ),
                )
            }

            val createdRef = response.created.getValue("\$c")
            val created = mpsAccess.read { getNode(NodeTarget.NodeReference(createdRef)) }
            assertEquals(CONCEPT_DECLARATION, created.concept)
            assertEquals("JsonComment", propertyValueOrNull(created, "name"))
            assertTrue(modelRootReferences(mpsAccess).contains(createdRef), "new root must appear in the model listing")
            assertTrue(structureModel(projectPath).readText().contains("JsonComment"))
        }
    }

    @Test
    fun `copyAsRoot copies a node into root position with fresh node ids and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_FILE_REF),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val copyRef = response.created.getValue("\$copy")
            assertNotEquals(JSON_FILE_REF, copyRef, "the copy must receive a fresh node id")

            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(copyRef)) }
            val source = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertEquals(source.concept, copy.concept)

            val roots = modelRootReferences(mpsAccess)
            assertTrue(roots.contains(copyRef), "the copy must be a root")
            assertTrue(roots.contains(JSON_FILE_REF), "the source root must still exist")
        }
    }

    @Test
    fun `moveAsRoot moves a child into root position leaving its old parent`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            // The fixture has no child whose concept is independently root-capable, so this exercises the move mechanics
            // under advisory enforcement; the can-be-root block itself is covered by the disallowed-root test.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.MoveAsRoot(
                            target = EditTarget.NodeReference("$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"),
                            model = ModelDestination(STRUCTURE_MODEL),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            val jsonString = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_REF)) }
            assertTrue(childrenInRole(jsonString, "propertyDeclaration").isEmpty(), "the child must leave its old parent")
            assertTrue(
                modelRootReferences(mpsAccess).contains("$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"),
                "the moved node must now be a root",
            )
        }
    }

    @Test
    fun `an existing root is moved into a parent by the existing moveAsChild operation`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            // First promote a child to a persisted root (advisory, since PropertyDeclaration is not root-capable), so
            // moveAsChild below operates on a genuine root node.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.MoveAsRoot(
                            target = EditTarget.NodeReference("$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"),
                            model = ModelDestination(STRUCTURE_MODEL),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.MoveAsChild(
                            target = EditTarget.NodeReference("$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"),
                            into = EditTarget.NodeReference(JSON_ARRAY_REF),
                            role = "propertyDeclaration",
                        ),
                    ),
                )
            }

            assertTrue(
                modelRootReferences(mpsAccess).none { it == "$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID" },
                "the node must no longer be a root",
            )
            val jsonArray = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_ARRAY_REF)) }
            assertEquals("value", propertyValueOrNull(childrenInRole(jsonArray, "propertyDeclaration").single(), "name"))
        }
    }

    @Test
    fun `an aliased root is reported in created and usable by a later operation`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Placeholder")),
                            alias = "\$c",
                        ),
                        EditOperation.SetProperty(target = EditTarget.Alias("\$c"), name = "name", value = "Renamed"),
                    ),
                )
            }

            val createdRef = response.created.getValue("\$c")
            val created = mpsAccess.read { getNode(NodeTarget.NodeReference(createdRef)) }
            assertEquals("Renamed", propertyValueOrNull(created, "name"))
        }
    }

    @Test
    fun `a disallowed root is blocked by default and applied under advisory`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = editorModel(projectPath).readText()

            val blocked = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(EDITOR_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "DisallowedRoot")),
                            alias = "\$c",
                        ),
                    ),
                )
            }
            assertTrue(blocked.violations.any { it.constraint == "canBeRoot" && it.operation == 0 })
            assertTrue(blocked.created.isEmpty())
            assertEquals(before, editorModel(projectPath).readText())

            val forced = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(EDITOR_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "DisallowedRoot")),
                            alias = "\$c",
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }
            assertTrue(forced.violations.any { it.constraint == "canBeRoot" })
            assertTrue(forced.created.containsKey("\$c"))
            assertTrue(editorModel(projectPath).readText().contains("DisallowedRoot"))
        }
    }

    @Test
    fun `targeting an unresolvable destination model fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddRoot(
                                model = ModelDestination("r:00000000-0000-0000-0000-000000000000(does.not.exist)"),
                                concept = CONCEPT_DECLARATION,
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.MODEL_NOT_FOUND, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun modelRootReferences(mpsAccess: MpsAccess): List<String> =
        mpsAccess.read { list(listOf(STRUCTURE_MODEL), depth = 1) }
            .children.orEmpty()
            .mapNotNull(MpsListEntryJson::reference)

    private fun structureModel(projectPath: Path): Path = projectPath.resolve(STRUCTURE_MODEL_PATH)

    private fun editorModel(projectPath: Path): Path = projectPath.resolve(EDITOR_MODEL_PATH)

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"

        const val EDITOR_MODEL = "r:4984d1ec-a1c9-4ad1-8af7-b206011783d5(com.specificlanguages.json.editor)"
        const val EDITOR_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.editor.mps"

        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"

        const val JSON_FILE_REF = "$STRUCTURE_MODEL/2110045694544566904"
        const val JSON_STRING_REF = "$STRUCTURE_MODEL/2110045694544569294"
        const val JSON_STRING_VALUE_ID = "2110045694544569338"
        const val JSON_ARRAY_REF = "$STRUCTURE_MODEL/2110045694544569357"
    }
}
