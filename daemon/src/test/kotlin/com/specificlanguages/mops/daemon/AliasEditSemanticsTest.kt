package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class AliasEditSemanticsTest {

    @Test
    fun `a later operation targets a node the batch created by alias`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = PROPERTY_DECLARATION,
                            alias = "\$p",
                        ),
                        EditOperation.SetProperty(target = EditTarget.Alias("\$p"), name = "name", value = "aliased"),
                    ),
                )
            }

            val createdReference = assertNotNull(response.created["\$p"])
            val added = mpsAccess.read { getNode(NodeTarget.NodeReference(createdReference)) }
            assertEquals("aliased", propertyValueOrNull(added, "name"))

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            assertNotNull(childrenInRole(jsonFile, "propertyDeclaration").singleOrNull { propertyValueOrNull(it, "name") == "aliased" })
            // The alias is batch-local: it is never written as a persisted attribute value. (A raw substring search
            // would be flaky, since MPS's random base-encoded node ids can contain "$p" by chance.)
            assertFalse(structureModel(projectPath).readText().contains("\"\$p\""))
        }
    }

    @Test
    fun `setReference points at a node created earlier in the same batch`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = PROPERTY_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "target")),
                            alias = "\$p",
                        ),
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "linkDeclaration",
                            concept = LINK_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "role", value = "link")),
                            alias = "\$l",
                        ),
                        EditOperation.SetReference(
                            target = EditTarget.Alias("\$l"),
                            role = "target",
                            to = EditTarget.Alias("\$p"),
                        ),
                    ),
                )
            }

            val propertyId = assertNotNull(response.created["\$p"]).substringAfterLast("/")
            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_REF)) }
            val link = childrenInRole(jsonFile, "linkDeclaration").single { propertyValueOrNull(it, "role") == "link" }
            val linkTarget = link.references?.single { it.role == "target" }?.target
            assertEquals(propertyId, linkTarget?.node)
        }
    }

    @Test
    fun `a forward reference to an alias not yet created fails and changes nothing`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.SetProperty(target = EditTarget.Alias("\$p"), name = "name", value = "early"),
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "propertyDeclaration",
                                concept = PROPERTY_DECLARATION,
                                alias = "\$p",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.TARGET_RESOLUTION_FAILED, exception.code)
            assertEquals(before, structureModel(projectPath).readText())
        }
    }

    @Test
    fun `redeclaring an alias fails and changes nothing`() {
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
                                alias = "\$p",
                            ),
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference(JSON_FILE_REF),
                                role = "propertyDeclaration",
                                concept = PROPERTY_DECLARATION,
                                alias = "\$p",
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.INVALID_REQUEST, exception.code)
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
    }
}
