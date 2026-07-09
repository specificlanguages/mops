package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.daemon.core.MpsAccess
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `replace` engine semantics against the baseLanguage sandbox, whose Calculator class carries ordered `member`
 * children (add, subtract, main) and a real inbound Reference (main() calls add), so slot preservation, identity
 * reuse, and the dangling-reference doctrine can be observed on live structure. Node ids are discovered by navigation.
 */
class ReplaceEditSemanticsTest {

    @Test
    fun `a fresh-spec replace swaps the node in place and keeps sibling order`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val beforeIds = members(mpsAccess).map { it.id }
            val subtractId = member(mpsAccess, "subtract").id!!
            val index = beforeIds.indexOf(subtractId)

            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = ref(subtractId),
                            with = InlineChild.Fresh(
                                concept = FIELD_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "result")),
                                children = listOf(InlineChild.Fresh(role = "type", concept = INTEGER_TYPE)),
                            ),
                            alias = "\$field",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val after = members(mpsAccess)
            assertEquals(beforeIds.size, after.size, "the member count is unchanged")
            val afterIds = after.map { it.id }
            // Every sibling but the replaced one keeps its exact position.
            assertEquals(
                beforeIds.filterIndexed { i, _ -> i != index },
                afterIds.filterIndexed { i, _ -> i != index },
                "siblings keep their order around the replacement",
            )
            val replacement = after[index]
            assertEquals("result", propertyValueOrNull(replacement, "name"))
            assertEquals(FIELD_DECLARATION, replacement.concept)
            assertNotEquals(subtractId, replacement.id, "the replacement gets a fresh id; the old subtree is gone")
            assertEquals(replacement.id, nodeId(response.created.getValue("field")))
        }
    }

    @Test
    fun `replacing a root with a fresh node whose leaves adopt referenced members keeps identities and the inbound call`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val calculator = calculator(mpsAccess)
            val addId = member(mpsAccess, "add").id!!
            val mainId = member(mpsAccess, "main").id!!

            // Replace the whole Calculator root with a fresh class that adopts `add` and `main` by identity; `subtract`
            // (the remainder) is dropped. A root target yields a root of the same model.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = ref(calculator.id!!),
                            with = InlineChild.Fresh(
                                concept = CLASS_CONCEPT,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "Calculator")),
                                children = listOf(
                                    InlineChild.Move(role = "member", source = ref(mainId)),
                                    InlineChild.Move(role = "member", source = ref(addId)),
                                ),
                            ),
                            alias = "\$calc",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val newCalc = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("calc"))) }
            // A root target yields a root of the same model: the replacement has no containing parent.
            assertNull(newCalc.parent, "the replacement of a root target must itself be a root")
            assertEquals(SANDBOX_MODEL, newCalc.model)
            val memberIds = childrenInRole(newCalc, "member").mapNotNull { it.id }.toSet()
            assertEquals(setOf(addId, mainId), memberIds, "adopted members keep their ids; subtract is gone")

            // The inbound call in main() still resolves to `add`: both were adopted by identity.
            val calledFromMain = references(childrenInRole(newCalc, "member").single { it.id == mainId })
                .single { it.role == "baseMethodDeclaration" && it.target.node == addId }
            assertTrue(calledFromMain.target.resolved, "the inbound method call must still resolve")
        }
    }

    @Test
    fun `a bare move leaf of a descendant unwraps it into the target's slot and binds the alias`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            // subtract() returns `a - b`; unwrap the MinusExpression, keeping its left operand (the read of `a`).
            val returnExpression = returnExpressionOf(member(mpsAccess, "subtract"))
            val minusId = returnExpression.id!!
            val leftId = childrenInRole(returnExpression, "leftExpression").single().id!!
            val rightId = childrenInRole(returnExpression, "rightExpression").single().id!!

            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = ref(minusId),
                            with = InlineChild.Move(role = null, source = ref(leftId)),
                            alias = "\$kept",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val subtractAfter = member(mpsAccess, "subtract")
            val newReturnExpression = returnExpressionOf(subtractAfter)
            assertEquals(leftId, newReturnExpression.id, "the kept operand rose into the MinusExpression's slot")
            assertEquals(leftId, nodeId(response.created.getValue("kept")))
            // The remainder — the wrapper and its right operand — was deleted.
            val remainingIds = allIds(subtractAfter)
            assertTrue(minusId !in remainingIds, "the unwrapped MinusExpression is gone")
            assertTrue(rightId !in remainingIds, "the discarded right operand is gone")
            assertTrue(leftId in remainingIds, "the kept operand survived by identity")
        }
    }

    @Test
    fun `a replace whose end state violates containment blocks and reverts, reporting the operation index`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, projectPath ->
            val middleId = members(mpsAccess)[1].id!!
            val before = sandboxModel(projectPath).readText()

            // An IntegerType is not a member; the end state violates the containment rule for the member role.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = ref(middleId),
                            with = InlineChild.Fresh(concept = INTEGER_TYPE),
                        ),
                    ),
                )
            }

            assertTrue(
                response.violations.any { it.operation == 0 && it.constraint == "containment" },
                "expected a containment violation at operation 0, got ${response.violations}",
            )
            assertEquals(before, sandboxModel(projectPath).readText(), "a blocked batch reverts")
        }
    }

    @Test
    fun `replacing a referenced node succeeds without warnings and leaves the inbound reference dangling`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val addId = member(mpsAccess, "add").id!!
            val subtractId = member(mpsAccess, "subtract").id!!

            // Replace `add` with a copy of `subtract` (a fresh id), so nothing preserves add's identity.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = ref(addId),
                            with = InlineChild.Copy(role = null, source = ref(subtractId)),
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "the dangling reference is not an edit-time violation")
            assertTrue(response.warnings.isEmpty(), "deleting a referenced node produces no warnings")

            // add's id is gone; main()'s call to it now dangles. mops does not detect or rewrite it at edit time —
            // the unresolved reference is left in the model (observable here, and to `mops model check`).
            val calledFromMain = references(member(mpsAccess, "main"))
                .single { it.role == "baseMethodDeclaration" && it.target.node == addId }
            assertTrue(!calledFromMain.target.resolved, "the inbound call must now be unresolved (dangling)")
        }
    }

    private fun returnExpressionOf(method: MpsNodeJson): MpsNodeJson {
        val body = childrenInRole(method, "body").single()
        val returnStatement = childrenInRole(body, "statement").single { it.concept == RETURN_STATEMENT }
        return childrenInRole(returnStatement, "expression").single()
    }

    private fun calculator(mpsAccess: MpsAccess): MpsNodeJson {
        val ref = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }
            .children!!.single { it.name == "Calculator" }.reference!!
        return mpsAccess.read { getNode(NodeTarget.NodeReference(ref)) }
    }

    private fun members(mpsAccess: MpsAccess): List<MpsNodeJson> = childrenInRole(calculator(mpsAccess), "member")

    private fun member(mpsAccess: MpsAccess, name: String): MpsNodeJson =
        members(mpsAccess).single { propertyValueOrNull(it, "name") == name }

    private fun ref(nodeId: String): EditTarget = EditTarget.NodeReference("$SANDBOX_MODEL/$nodeId")

    private fun nodeId(reference: String): String = reference.substringAfterLast('/')

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun references(node: MpsNodeJson): List<MpsNodeReferenceJson> =
        node.references.orEmpty() + node.children.orEmpty().flatMap { references(it) }

    private fun allIds(node: MpsNodeJson): Set<String> =
        (listOfNotNull(node.id) + node.children.orEmpty().flatMap { allIds(it) }).toSet()

    private fun sandboxModel(projectPath: Path): Path = projectPath.resolve(SANDBOX_MODEL_PATH)

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
        const val SANDBOX_MODEL_PATH = "solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps"

        const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
        const val FIELD_DECLARATION = "jetbrains.mps.baseLanguage.structure.FieldDeclaration"
        const val INTEGER_TYPE = "jetbrains.mps.baseLanguage.structure.IntegerType"
        const val RETURN_STATEMENT = "jetbrains.mps.baseLanguage.structure.ReturnStatement"
    }
}
