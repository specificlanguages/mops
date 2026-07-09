package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.InlineReference
import com.specificlanguages.mops.protocol.ModelDestination
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

class InlineLeafEditSemanticsTest {

    @Test
    fun `a move leaf adopts an existing node into new structure preserving its identity`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "MoveHolder")),
                            children = listOf(
                                moveLeaf(role = "propertyDeclaration", source = JSON_STRING_VALUE_REF),
                            ),
                            alias = "\$holder",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val holder = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("holder"))) }
            val moved = childrenInRole(holder, "propertyDeclaration").single()
            // Identity preserved: the adopted node keeps its exact node id, so any inbound reference still resolves.
            assertEquals(JSON_STRING_VALUE_ID, moved.id)
            assertEquals("value", propertyValueOrNull(moved, "name"))

            // The source parent no longer holds it.
            val jsonString = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_REF)) }
            assertTrue(childrenInRole(jsonString, "propertyDeclaration").none { it.id == JSON_STRING_VALUE_ID })
        }
    }

    @Test
    fun `a copy leaf deep-copies an existing node with a fresh id and leaves the source intact`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "CopyHolder")),
                            children = listOf(
                                copyLeaf(role = "propertyDeclaration", source = JSON_STRING_VALUE_REF),
                            ),
                            alias = "\$holder",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val holder = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("holder"))) }
            val copy = childrenInRole(holder, "propertyDeclaration").single()
            assertEquals("value", propertyValueOrNull(copy, "name"))
            assertNotEquals(JSON_STRING_VALUE_ID, copy.id, "the copy must receive a fresh node id")

            // The source is untouched.
            val jsonString = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_REF)) }
            assertTrue(childrenInRole(jsonString, "propertyDeclaration").any { it.id == JSON_STRING_VALUE_ID })
        }
    }

    @Test
    fun `a move leaf with a read-only source fails like moveAsChild and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val existing = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val extendsTarget = existing.references!!.single { it.role == "extends" }.target
            val readOnlyReference = "${extendsTarget.model}/${extendsTarget.node}"
            val before = structureModel(projectPath).readText()

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(STRUCTURE_MODEL),
                                concept = CONCEPT_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "AdoptsReadOnly")),
                                children = listOf(
                                    moveLeaf(role = "propertyDeclaration", source = readOnlyReference),
                                ),
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.MODEL_READ_ONLY, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `end-state constraint checking covers a node placed via a leaf`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            // Copying a ConceptDeclaration under the propertyDeclaration role (which expects PropertyDeclaration) is a
            // containment violation, proving leaf placements are recorded and checked at end state.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "BadLeafHost")),
                            children = listOf(
                                copyLeaf(role = "propertyDeclaration", source = JSON_ARRAY_REF),
                            ),
                        ),
                    ),
                )
            }

            assertTrue(response.violations.any { it.operation == 0 && it.constraint == "containment" })
            assertEquals(before, structureModel(projectPath).readText(), "a blocked batch reverts")
        }
    }

    @Test
    fun `an inline reference in the canonical to form points a new node at an existing target`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "linkDeclaration",
                            concept = LINK_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "role", value = "viaTo")),
                            references = listOf(
                                InlineReference(
                                    role = "target",
                                    to = EditTarget.NodeReference("$STRUCTURE_MODEL/$IJSON_VALUE_ID"),
                                ),
                            ),
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val link = childrenInRole(jsonFile, "linkDeclaration").single { propertyValueOrNull(it, "role") == "viaTo" }
            assertEquals(IJSON_VALUE_ID, link.references?.single { it.role == "target" }?.target?.node)
        }
    }

    private fun moveLeaf(role: String, source: String): InlineChild =
        InlineChild.Move(role = role, source = EditTarget.NodeReference(source))

    private fun copyLeaf(role: String, source: String): InlineChild =
        InlineChild.Copy(role = role, source = EditTarget.NodeReference(source))

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun structureModel(projectPath: Path): Path = projectPath.resolve(STRUCTURE_MODEL_PATH)

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"

        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"

        const val JSON_FILE_REF = "$STRUCTURE_MODEL/2110045694544566904"
        const val JSON_STRING_REF = "$STRUCTURE_MODEL/2110045694544569294"
        const val JSON_STRING_VALUE_ID = "2110045694544569338"
        const val JSON_STRING_VALUE_REF = "$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"
        const val JSON_ARRAY_REF = "$STRUCTURE_MODEL/2110045694544569357"
        const val IJSON_VALUE_ID = "2110045694544566909"
    }
}
