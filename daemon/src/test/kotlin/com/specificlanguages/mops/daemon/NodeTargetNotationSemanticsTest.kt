package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineReference
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * A node target written as one string means the same on every supported MPS version, whether a node command or an edit
 * reference target gives it: `<model>/<node ID>` with a model name or serialized model reference, or a serialized node
 * reference, with the node ID in decimal or encoded spelling.
 */
class NodeTargetNotationSemanticsTest {

    @Test
    fun `a single-token node target resolves a model name or reference with either node ID spelling`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val encodedId = encoded(mpsAccess, JSON_FILE_ID)
            assertNotEquals(JSON_FILE_ID, encodedId)

            listOf(
                "$STRUCTURE_MODEL_NAME/$JSON_FILE_ID",
                "$STRUCTURE_MODEL_NAME/$encodedId",
                "$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_ID",
                "$STRUCTURE_MODEL_REFERENCE/$encodedId",
            ).forEach { target ->
                val node = mpsAccess.read { getNode(NodeTarget.NodeReference(target)) }
                assertEquals(JSON_FILE_ID, node.id, target)
            }
        }
    }

    @Test
    fun `a single-token node target keeps the CLI's error codes`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val missingNode = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.NodeReference("$STRUCTURE_MODEL_NAME/1")) }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, missingNode.code)

            val missingModel = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.NodeReference("no.such.model/$JSON_FILE_ID")) }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, missingModel.code)

            // A malformed target fails with MPS's own parse error, which the daemon reports as GENERIC_FAILURE.
            assertFailsWith<IllegalArgumentException> {
                mpsAccess.read { getNode(NodeTarget.NodeReference("malformed")) }
            }

            val missingModelInModel =assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.InModel(modelTarget = "no.such.model", nodeId = JSON_FILE_ID)) }
            }
            assertEquals(MpsErrorCode.MODEL_NOT_FOUND, missingModelInModel.code)
        }
    }

    @Test
    fun `an inline reference target resolves a model name with an encoded node ID`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val encodedId = encoded(mpsAccess, IJSON_VALUE_ID)
            mpsAccess.write {
                modelEdit(
                    EditBatch(
                        listOf(
                            EditOperation.AddChild(
                                target = EditTarget.NodeReference("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID"),
                                role = "linkDeclaration",
                                concept = LINK_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "role", value = "extraLink")),
                                references = listOf(
                                    InlineReference(
                                        role = "target",
                                        target = MpsNodeReferenceTargetJson(model = STRUCTURE_MODEL_NAME, node = encodedId),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }

            val jsonFile = mpsAccess.read { getNode(NodeTarget.NodeReference("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID")) }
            val link = jsonFile.children.orEmpty().single { it.role == "linkDeclaration" && propertyValueOrNull(it, "role") == "extraLink" }
            assertEquals(IJSON_VALUE_ID, link.references?.single { it.role == "target" }?.target?.node)
        }
    }

    private fun encoded(mpsAccess: MpsAccess, decimalId: String): String =
        mpsAccess.read { IdEncoder().toText(PersistenceFacade.getInstance().createNodeId(decimalId)) }

    private companion object {
        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6($STRUCTURE_MODEL_NAME)"
        const val JSON_FILE_ID = "2110045694544566904"
        const val IJSON_VALUE_ID = "2110045694544566909"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
    }
}
