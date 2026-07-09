package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuralEditSemanticsTest {

    @Test
    fun `addChild adds a property declaration under a named role and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = PROPERTY_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "active")),
                        ),
                    ),
                )
            }

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val added = childrenInRole(jsonFile, "propertyDeclaration").single { propertyValueOrNull(it, "name") == "active" }
            assertEquals(PROPERTY_DECLARATION, added.concept)
            assertContains(structureModel(projectPath).readText(), """value="active"""")
        }
    }

    @Test
    fun `addChild with an inline subtree creates properties and a reference to an existing node`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "linkDeclaration",
                            concept = LINK_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "role", value = "extraLink")),
                            references = listOf(
                                MpsNodeReferenceJson(
                                    role = "target",
                                    target = MpsNodeReferenceTargetJson(node = IJSON_VALUE_ID),
                                ),
                            ),
                        ),
                    ),
                )
            }

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val link = childrenInRole(jsonFile, "linkDeclaration").single { propertyValueOrNull(it, "role") == "extraLink" }
            val target = link.references?.single { it.role == "target" }?.target?.node
            assertEquals(IJSON_VALUE_ID, target)
            val persisted = structureModel(projectPath).readText()
            assertContains(persisted, """value="extraLink"""")
        }
    }

    @Test
    fun `deleteNode removes a node and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(batchOf(EditOperation.Delete(target = EditTarget.NodeReference(JSON_NULL_REF))))
            }

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_NULL_REF)) }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, exception.code)
            assertFalse(structureModel(projectPath).readText().contains("JsonNull"))
        }
    }

    @Test
    fun `deleteChild removes the single child in a role and persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.DeleteChild(
                            target = EditTarget.NodeReference(JSON_STRING_REF),
                            role = "propertyDeclaration",
                            position = ChildPosition.Only,
                        ),
                    ),
                )
            }

            val jsonString = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_REF)) }
            assertTrue(childrenInRole(jsonString, "propertyDeclaration").isEmpty())
            assertFalse(structureModel(projectPath).readText().contains("""value="2110045694544569338""""))
        }
    }

    @Test
    fun `moveAsChild relocates a node to a new parent and role`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.MoveAsChild(
                            target = EditTarget.InModel(modelTarget = STRUCTURE_MODEL, nodeId = JSON_STRING_VALUE_ID),
                            into = EditTarget.NodeReference(JSON_ARRAY_REF),
                            role = "propertyDeclaration",
                        ),
                    ),
                )
            }

            val jsonString = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_STRING_REF)) }
            val jsonArray = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_ARRAY_REF)) }
            assertTrue(childrenInRole(jsonString, "propertyDeclaration").isEmpty())
            val moved = childrenInRole(jsonArray, "propertyDeclaration").single()
            assertEquals("value", propertyValueOrNull(moved, "name"))
        }
    }

    @Test
    fun `unknown concept fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "propertyDeclaration",
                                concept = "com.does.not.Exist",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
            // The edit path shares find's diagnosis: the operation index is kept and the unknown language is named.
            val message = assertNotNull(exception.message)
            assertContains(message, "operation 0")
            assertContains(message, "\"com.does.not\" is not a module known to this project")
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `a mistyped concept in a loaded language suggests alternatives and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "propertyDeclaration",
                                concept = "jetbrains.mps.lang.structure.structure.PropertyDeclaratn",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
            val message = assertNotNull(exception.message)
            assertContains(message, "did you mean")
            assertContains(message, "PropertyDeclaration")
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `unknown containment role fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "noSuchRole",
                                concept = PROPERTY_DECLARATION,
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
    fun `moving a node into itself fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.MoveAsChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                into = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "implements",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.INVALID_REQUEST, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `a structural batch that fails midway leaves the model unchanged`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "propertyDeclaration",
                                concept = PROPERTY_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "ephemeral")),
                            ),
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_STRING_REF),
                                role = "noSuchRole",
                                concept = PROPERTY_DECLARATION,
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.ROLE_NOT_FOUND, exception.code)

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertNull(childrenInRole(jsonFile, "propertyDeclaration").firstOrNull { propertyValueOrNull(it, "name") == "ephemeral" })
            assertEquals(before, structureModel(projectPath).readText())
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

        const val JSON_FILE_REF = "$STRUCTURE_MODEL/2110045694544566904"
        const val JSON_STRING_REF = "$STRUCTURE_MODEL/2110045694544569294"
        const val JSON_STRING_VALUE_ID = "2110045694544569338"
        const val JSON_ARRAY_REF = "$STRUCTURE_MODEL/2110045694544569357"
        const val JSON_NULL_REF = "$STRUCTURE_MODEL/2110045694544732187"
        const val IJSON_VALUE_ID = "2110045694544566909"
    }
}
