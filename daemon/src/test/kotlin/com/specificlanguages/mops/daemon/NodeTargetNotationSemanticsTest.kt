package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineReference
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceTargetJson
import com.specificlanguages.mops.protocol.NodeTarget
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

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
                val node = mpsAccess.read { getNode(NodeTarget.SingleString(target)) }
                assertEquals(JSON_FILE_ID, node.id, target)
            }
        }
    }

    @Test
    fun `a single-token node target keeps the CLI's error codes`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val missingNode = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.SingleString("$STRUCTURE_MODEL_NAME/1")) }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, missingNode.code)

            val missingModel = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.SingleString("no.such.model/$JSON_FILE_ID")) }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, missingModel.code)

            val missingModelReference = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    getNode(NodeTarget.SingleString("r:00000000-0000-0000-0000-000000000000(no.such.model)/$JSON_FILE_ID"))
                }
            }
            assertEquals(MpsErrorCode.NODE_NOT_FOUND, missingModelReference.code)

            // The daemon reports an IllegalArgumentException as GENERIC_FAILURE with its message.
            listOf("malformed", "$STRUCTURE_MODEL_NAME/not-a-node-id").forEach { target ->
                val malformed = assertFailsWith<IllegalArgumentException>(target) {
                    mpsAccess.read { getNode(NodeTarget.SingleString(target)) }
                }
                assertContains(assertNotNull(malformed.message), "accepted notations: <model>/<node ID>", message = target)
            }

            val missingModelInModel = assertFailsWith<MpsRequestException> {
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
                                target = EditTarget.SingleString("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID"),
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

            val jsonFile = mpsAccess.read { getNode(NodeTarget.SingleString("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID")) }
            val link = jsonFile.children.orEmpty().single { it.role == "linkDeclaration" && propertyValueOrNull(it, "role") == "extraLink" }
            assertEquals(IJSON_VALUE_ID, link.references?.single { it.role == "target" }?.target?.node)
        }
    }

    @Test
    fun `a model name shared by two models fails as ambiguous in commands, Code Mode and edits`() {
        SharedMpsEnvironment.withOpenProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                model.resolveSibling("duplicate.structure.mps").writeText(
                    model.readText().replace(STRUCTURE_MODEL_REFERENCE, DUPLICATE_MODEL_REFERENCE),
                )
            },
        ) { project, _ ->
            val mpsAccess = SharedMpsEnvironment.mpsAccess(project)

            val command = assertFailsWith<MpsRequestException> {
                mpsAccess.read { getNode(NodeTarget.SingleString("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID")) }
            }
            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, command.code)

            // A serialized node reference names one of the two models by its ID.
            val duplicate = mpsAccess.read { getNode(NodeTarget.SingleString("$DUPLICATE_MODEL_REFERENCE/$JSON_FILE_ID")) }
            assertEquals(DUPLICATE_MODEL_REFERENCE, duplicate.model)

            val codeMode = CodeModeExecutor(mpsAccess, project, SharedMpsEnvironment.platform).execute(
                CodeRunRequest("", """
                    project.read {
                        try {
                            mops.lookup.node('$STRUCTURE_MODEL_NAME/$JSON_FILE_ID')
                            assert false: 'Expected an ambiguous model'
                        } catch (com.specificlanguages.mops.daemon.core.MpsRequestException failure) {
                            assert failure.code.name() == 'AMBIGUOUS_TARGET': failure
                        }
                    }
                    return 'ok'
                """.trimIndent(), "lookup-node-ambiguous.groovy"),
            )
            assertEquals("ok", codeMode.output)

            val inlineReference = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            listOf(
                                addLink(
                                    target = EditTarget.SingleString("$DUPLICATE_MODEL_REFERENCE/$JSON_FILE_ID"),
                                    referenceModel = STRUCTURE_MODEL_NAME,
                                ),
                            ),
                        ),
                    )
                }
            }
            // An edit keeps the lookup's error code and names the operation.
            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, inlineReference.code)
            assertContains(
                inlineReference.message,
                "operation 0 reference target could not be resolved: ambiguous model target",
            )

            val editTarget = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            listOf(
                                addLink(
                                    target = EditTarget.SingleString("$STRUCTURE_MODEL_NAME/$JSON_FILE_ID"),
                                    referenceModel = DUPLICATE_MODEL_REFERENCE,
                                ),
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, editTarget.code)
            assertContains(editTarget.message, "operation 0 target could not be resolved: ambiguous model target")

            // A malformed target is not a lookup failure, so an edit reports it as TARGET_RESOLUTION_FAILED.
            val malformedInlineReference = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            listOf(
                                addLink(
                                    target = EditTarget.SingleString("$DUPLICATE_MODEL_REFERENCE/$JSON_FILE_ID"),
                                    referenceModel = STRUCTURE_MODEL_NAME,
                                    referenceNodeId = "not-a-node-id",
                                ),
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.TARGET_RESOLUTION_FAILED, malformedInlineReference.code)
            assertContains(
                malformedInlineReference.message,
                "operation 0 reference target could not be resolved: invalid node target",
            )
        }
    }

    private fun addLink(target: EditTarget, referenceModel: String, referenceNodeId: String = IJSON_VALUE_ID) =
        EditOperation.AddChild(
            target = target,
            role = "linkDeclaration",
            concept = LINK_DECLARATION,
            properties = listOf(MpsNodePropertyJson(name = "role", value = "extraLink")),
            references = listOf(
                InlineReference(
                    role = "target",
                    target = MpsNodeReferenceTargetJson(model = referenceModel, node = referenceNodeId),
                ),
            ),
        )

    private fun encoded(mpsAccess: MpsAccess, decimalId: String): String =
        mpsAccess.read { IdEncoder().toText(PersistenceFacade.getInstance().createNodeId(decimalId)) }

    private companion object {
        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6($STRUCTURE_MODEL_NAME)"
        const val JSON_FILE_ID = "2110045694544566904"
        const val IJSON_VALUE_ID = "2110045694544566909"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
        const val DUPLICATE_MODEL_REFERENCE = "r:11111111-2222-4333-8444-555555555555($STRUCTURE_MODEL_NAME)"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
    }
}
