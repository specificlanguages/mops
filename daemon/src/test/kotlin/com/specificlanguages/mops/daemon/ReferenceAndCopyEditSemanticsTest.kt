package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ReferenceAndCopyEditSemanticsTest {

    @Test
    fun `setReference rewires a reference to a same-model target and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val datatypeId = numberDatatypeId(mpsAccess)
            val referencesBefore = numberDatatypeReferenceCount(projectPath)

            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "dataType",
                            to = EditTarget.NodeReference("$STRUCTURE_MODEL/$datatypeId"),
                        ),
                    ),
                )
            }

            val value = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_VALUE_REF)) }
            val dataType = value.references?.single { it.role == "dataType" }?.target
            assertNull(dataType?.model)
            assertEquals(datatypeId, dataType?.node)
            assertEquals(referencesBefore + 1, numberDatatypeReferenceCount(projectPath))
        }
    }

    @Test
    fun `setReference clears a reference and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "dataType",
                            to = null,
                        ),
                    ),
                )
            }

            val value = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_VALUE_REF)) }
            assertNull(value.references?.firstOrNull { it.role == "dataType" })
        }
    }

    @Test
    fun `setReference supports a cross-model target`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val original = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_VALUE_REF)) }
            val externalTarget = original.references!!.single { it.role == "dataType" }.target
            val externalModel = requireNotNull(externalTarget.model) { "expected a cross-model dataType target" }
            val externalReference = "$externalModel/${externalTarget.node}"

            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "dataType",
                            to = null,
                        ),
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "dataType",
                            to = EditTarget.NodeReference(externalReference),
                        ),
                    ),
                )
            }

            val rewired = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_VALUE_REF)) }
            val target = rewired.references!!.single { it.role == "dataType" }.target
            assertEquals(externalModel, target.model)
            assertEquals(externalTarget.node, target.node)
        }
    }

    @Test
    fun `copyAsChild duplicates a node with a fresh id and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val propertyDeclarationsBefore = propertyDeclarationCount(projectPath)

            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsChild(
                            target = EditTarget.NodeReference(JSON_ARRAY_REF),
                            source = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "propertyDeclaration",
                        ),
                    ),
                )
            }

            val jsonArray = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_ARRAY_REF)) }
            val copy = childrenInRole(jsonArray, "propertyDeclaration").single()
            assertEquals("value", propertyValueOrNull(copy, "name"))
            assertNotEquals(JSON_STRING_VALUE_ID, copy.id)

            val source = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_VALUE_REF)) }
            assertEquals("value", propertyValueOrNull(source, "name"))
            assertEquals(propertyDeclarationsBefore + 1, propertyDeclarationCount(projectPath))
        }
    }

    @Test
    fun `copyAsChild rewires a reference within the copied subtree to point at the copy`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            // The fixture has no multi-node subtree with an internal reference, so build one: point JsonArray's
            // `items` link at JsonArray itself, giving the subtree a reference back into itself. Then copy the
            // whole subtree and confirm the copy's reference follows the copy, not the original.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference(JSON_ARRAY_ITEMS_LINK_REF),
                            role = "target",
                            to = EditTarget.NodeReference(JSON_ARRAY_REF),
                        ),
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_ARRAY_REF),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val copyRef = response.created.getValue("copy")
            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(copyRef)) }
            val copiedLinkTarget = childrenInRole(copy, "linkDeclaration").single()
                .references!!.single { it.role == "target" }.target

            // The copied link resolves within the copy, not back into the original subtree.
            assertNull(copiedLinkTarget.model, "an internal reference stays within the same model")
            assertEquals(copy.id, copiedLinkTarget.node, "the copy's reference must target the copy")
            assertNotEquals(JSON_ARRAY_ID, copiedLinkTarget.node, "the copy must not reference the original")

            // The original subtree is untouched: its link still points at the original array.
            val source = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_ARRAY_REF)) }
            val sourceLinkTarget = childrenInRole(source, "linkDeclaration").single()
                .references!!.single { it.role == "target" }.target
            assertEquals(JSON_ARRAY_ID, sourceLinkTarget.node, "the original reference is unchanged")
        }
    }

    @Test
    fun `setReference with an unknown role fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.SetReference(
                                target = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                                role = "noSuchReference",
                                to = EditTarget.NodeReference(JSON_ARRAY_REF),
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.ROLE_NOT_FOUND, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `copyAsChild with an unresolvable source fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.CopyAsChild(
                                target = EditTarget.NodeReference(JSON_ARRAY_REF),
                                source = EditTarget.NodeReference("$STRUCTURE_MODEL/9999999999999999999"),
                                role = "propertyDeclaration",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun structureModel(projectPath: Path): Path = projectPath.resolve(STRUCTURE_MODEL_PATH)

    // Number of persisted PropertyDeclaration nodes, matched by the concept's persistence index.
    private fun propertyDeclarationCount(projectPath: Path): Int =
        structureModel(projectPath).readText().split("""concept="1TJgyi"""").size - 1

    // Number of persisted references resolving to the JsonNumberDatatype datatype.
    private fun numberDatatypeReferenceCount(projectPath: Path): Int =
        structureModel(projectPath).readText().split("""resolve="JsonNumberDatatype"""").size - 1

    // The datatype JsonNumber.value points at, read from the model rather than hard-coded from persistence ids.
    private fun numberDatatypeId(mpsAccess: com.specificlanguages.mops.daemon.core.MpsAccess): String {
        val jsonNumber = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_NUMBER_REF)) }
        val value = childrenInRole(jsonNumber, "propertyDeclaration").single { propertyValueOrNull(it, "name") == "value" }
        return requireNotNull(value.references!!.single { it.role == "dataType" }.target.node)
    }

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"

        const val JSON_STRING_VALUE_ID = "2110045694544569338"
        const val JSON_STRING_VALUE_REF = "$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"
        const val JSON_ARRAY_ID = "2110045694544569357"
        const val JSON_ARRAY_REF = "$STRUCTURE_MODEL/$JSON_ARRAY_ID"

        // JsonArray's `items` LinkDeclaration child.
        const val JSON_ARRAY_ITEMS_LINK_REF = "$STRUCTURE_MODEL/2110045694544569360"
        const val JSON_NUMBER_REF = "$STRUCTURE_MODEL/2110045694544569437"
    }
}
