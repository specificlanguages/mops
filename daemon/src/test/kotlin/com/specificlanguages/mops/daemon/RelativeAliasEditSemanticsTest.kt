package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.ChildPosition
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
import kotlin.test.assertTrue

/**
 * Relative-alias addressing against the baseLanguage sandbox: a node created earlier in a batch (here, a copied
 * Calculator) is reached by descending a Containment Role path from its alias, so a later operation edits inside the
 * fresh subtree without a `get-node` round-trip to learn a descendant's id. baseLanguage is always loaded, so the copy
 * applies without an unbuilt-language guard. Node ids and role indices are discovered by navigation.
 */
class RelativeAliasEditSemanticsTest {

    @Test
    fun `a relative role path modifies a grandchild of an aliased copy in the same batch`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val calculator = calculator(mpsAccess)
            val calculatorId = calculator.id!!
            val members = childrenInRole(calculator, "member")
            val addIndex = members.indexOfFirst { propertyValueOrNull(it, "name") == "add" }
            assertTrue(addIndex >= 0, "the fixture Calculator must have an `add` method")
            val originalFirstParamName =
                propertyValueOrNull(childrenInRole(members[addIndex], "parameter").first(), "name")

            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(SANDBOX_MODEL),
                            source = ref(calculatorId),
                            alias = "\$copy",
                        ),
                        // Rename the first parameter of the copied `add` method — a grandchild of $copy — addressed by
                        // role path and position, without ever learning its id.
                        EditOperation.SetProperty(
                            target = EditTarget.RelativeAlias(
                                "\$copy",
                                listOf(
                                    EditTarget.PathStep("member", ChildPosition.Index(addIndex)),
                                    EditTarget.PathStep("parameter", ChildPosition.First),
                                ),
                            ),
                            name = "name",
                            value = "renamed",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            // The copy's add-parameter took the new name...
            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            val copiedAdd = childrenInRole(copy, "member").single { propertyValueOrNull(it, "name") == "add" }
            assertEquals("renamed", propertyValueOrNull(childrenInRole(copiedAdd, "parameter").first(), "name"))

            // ...and the original is untouched: relative addressing reached only the copy.
            val original = mpsAccess.read { getNode(NodeTarget.NodeReference("$SANDBOX_MODEL/$calculatorId")) }
            val originalAdd = childrenInRole(original, "member").single { propertyValueOrNull(it, "name") == "add" }
            assertEquals(
                originalFirstParamName,
                propertyValueOrNull(childrenInRole(originalAdd, "parameter").first(), "name"),
            )
        }
    }

    @Test
    fun `a relative path with an unknown role fails and reverts the whole batch`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, projectPath ->
            val calculatorId = calculator(mpsAccess).id!!
            val before = sandboxModel(projectPath).readText()

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        batchOf(
                            EditOperation.CopyAsRoot(
                                model = ModelDestination(SANDBOX_MODEL),
                                source = ref(calculatorId),
                                alias = "\$copy",
                            ),
                            EditOperation.SetProperty(
                                target = EditTarget.RelativeAlias(
                                    "\$copy",
                                    listOf(EditTarget.PathStep("noSuchRole", ChildPosition.Only)),
                                ),
                                name = "name",
                                value = "x",
                            ),
                        ),
                    )
                }
            }

            assertEquals(MpsErrorCode.ROLE_NOT_FOUND, exception.code)
            assertTrue(exception.message!!.contains("operation 1"), "the error must carry the operation index")
            // The earlier copy is rolled back too: a failed relative-path edit leaves the model exactly as it was.
            assertEquals(before, sandboxModel(projectPath).readText())
        }
    }

    private fun calculator(mpsAccess: MpsAccess): MpsNodeJson {
        val reference = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }
            .children!!.single { it.name == "Calculator" }.reference!!
        return mpsAccess.read { getNode(NodeTarget.NodeReference(reference)) }
    }

    private fun ref(nodeId: String): EditTarget = EditTarget.NodeReference("$SANDBOX_MODEL/$nodeId")

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun sandboxModel(projectPath: Path): Path = projectPath.resolve(SANDBOX_MODEL_PATH)

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
        const val SANDBOX_MODEL_PATH = "solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps"
    }
}
