package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeReferenceJson
import com.specificlanguages.mops.protocol.NodeTarget
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `wrap` / `unwrap` engine semantics against the baseLanguage sandbox. The Calculator methods give real expression
 * trees to wrap and strip (add: `a + b`; subtract: `return a - b`; main: `println("2 + 2 = " + new Calculator()
 * .add(2, 2))`). Node ids are discovered by navigation.
 */
class WrapUnwrapEditSemanticsTest {

    @Test
    fun `wrap puts the wrapper in the target's slot, adopts the target under the role, and builds inline siblings`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val addExpression = expressionStatementExpression(member(mpsAccess, "add"))
            val plusId = addExpression.id!!
            val operandIds = addExpression.children!!.mapNotNull { it.id }.toSet()

            // Wrap `a + b` in a MinusExpression: the target becomes its left operand, a fresh IntegerConstant its right.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Wrap(
                            target = ref(plusId),
                            concept = MINUS_EXPRESSION,
                            role = "leftExpression",
                            children = listOf(InlineChild.Fresh(role = "rightExpression", concept = INTEGER_CONSTANT)),
                            alias = "\$w",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            // The wrapper took the target's exact slot (the ExpressionStatement's single expression).
            val wrapper = expressionStatementExpression(member(mpsAccess, "add"))
            assertEquals(MINUS_EXPRESSION, wrapper.concept)
            assertEquals(nodeId(response.created.getValue("w")), wrapper.id)

            // The target moved under the wrapper's role, keeping its identity and its whole subtree.
            val left = childrenInRole(wrapper, "leftExpression").single()
            assertEquals(plusId, left.id, "the target is adopted under leftExpression by identity")
            assertEquals(operandIds, left.children!!.mapNotNull { it.id }.toSet(), "its subtree travelled intact")

            // The inline sibling was built under the other role; the wrapper holds no copy of the target's subtree.
            val right = childrenInRole(wrapper, "rightExpression").single()
            assertEquals(INTEGER_CONSTANT, right.concept)
            assertTrue(operandIds.none { it == right.id }, "the wrapper's sibling is fresh, not a copy of the target")
        }
    }

    @Test
    fun `unwrap promotes a direct child into the target's slot`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            // subtract() returns `a - b`; strip the MinusExpression, keeping its left operand.
            val minus = returnExpressionOf(member(mpsAccess, "subtract"))
            val minusId = minus.id!!
            val leftId = childrenInRole(minus, "leftExpression").single().id!!
            val rightId = childrenInRole(minus, "rightExpression").single().id!!

            val response = mpsAccess.write {
                modelEdit(batchOf(EditOperation.Unwrap(target = ref(minusId), keep = ref(leftId))))
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val subtractAfter = member(mpsAccess, "subtract")
            assertEquals(leftId, returnExpressionOf(subtractAfter).id, "the kept child rose into the slot")
            val ids = allIds(subtractAfter)
            assertTrue(minusId !in ids && rightId !in ids, "the stripped wrapper and its other child are gone")
        }
    }

    @Test
    fun `unwrap promotes a deep descendant in one operation`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            // main() prints `"2 + 2 = " + new Calculator().add(2, 2)`. Strip the whole string-concatenation argument,
            // keeping one deeply-nested integer constant — a multi-level strip in a single operation.
            val printlnArgument = printlnArgumentOf(mpsAccess)
            val plusId = printlnArgument.id!!
            val deepConstant = allNodes(printlnArgument).first { it.concept == INTEGER_CONSTANT && it.id != plusId }
            val keptId = deepConstant.id!!

            val response = mpsAccess.write {
                modelEdit(batchOf(EditOperation.Unwrap(target = ref(plusId), keep = ref(keptId))))
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val newArgument = printlnArgumentOf(mpsAccess)
            assertEquals(keptId, newArgument.id, "the deep descendant rose into the argument slot")
            assertEquals(INTEGER_CONSTANT, newArgument.concept)
            assertTrue(plusId !in allIds(printlnStatementOf(mpsAccess)), "the stripped subtree is gone")
        }
    }

    @Test
    fun `unwrap fails when keep is not a proper descendant of target`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, projectPath ->
            val addExpressionId = expressionStatementExpression(member(mpsAccess, "add")).id!!
            val unrelatedId = returnExpressionOf(member(mpsAccess, "subtract")).id!!
            val before = sandboxModel(projectPath).readText()

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(batchOf(EditOperation.Unwrap(target = ref(addExpressionId), keep = ref(unrelatedId))))
                }
            }
            assertEquals(MpsErrorCode.INVALID_REQUEST, exception.code)
            assertTrue(exception.message!!.contains("descendant"), "the error must name the intent rule: ${exception.message}")
            assertTrue(exception.message!!.contains("operation 0"), "the error must carry the operation index")
            assertEquals(before, sandboxModel(projectPath).readText(), "a rejected unwrap changes nothing")
        }
    }

    @Test
    fun `unwrapping a root promotes the kept node to a root of the same model`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            // Calculator holds an inner class `Inner` (a ClassConcept, which is root-valid). Unwrap Calculator keeping
            // Inner: it rises from a nested member to a Root Node of the same model.
            val innerId = member(mpsAccess, "Inner").id!!

            val response = mpsAccess.write {
                modelEdit(batchOf(EditOperation.Unwrap(target = ref(calculator(mpsAccess).id!!), keep = ref(innerId))))
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val promoted = mpsAccess.read { getNode(NodeTarget.NodeReference("$SANDBOX_MODEL/$innerId")) }
            assertNull(promoted.parent, "the kept node became a root of the same model")
            assertEquals(SANDBOX_MODEL, promoted.model)
            val roots = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }.children!!.mapNotNull { it.name }
            assertEquals(listOf("Inner"), roots, "Inner is now the model's only root; the old Calculator is gone")
        }
    }

    @Test
    fun `wrapping a root nests it under a fresh root of the same model`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val calculatorId = calculator(mpsAccess).id!!

            // Wrap the Calculator root in a fresh outer class, holding it under the (root-valid) `member` role as an
            // inner class. The wrapper becomes the root; Calculator is nested under it.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Wrap(
                            target = ref(calculatorId),
                            concept = CLASS_CONCEPT,
                            role = "member",
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Outer")),
                            alias = "\$outer",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val outer = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("outer"))) }
            assertNull(outer.parent, "the wrapper is a root of the same model")
            assertEquals(SANDBOX_MODEL, outer.model)
            val nested = childrenInRole(outer, "member").single { it.id == calculatorId }
            assertEquals("Calculator", propertyValueOrNull(nested, "name"), "Calculator is nested under the wrapper")
            val roots = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }.children!!.mapNotNull { it.name }
            assertEquals(listOf("Outer"), roots, "the wrapper is the model's only root")
        }
    }

    @Test
    fun `a wrap whose end state leaves an obligatory role empty blocks and reverts`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, projectPath ->
            val plusId = expressionStatementExpression(member(mpsAccess, "add")).id!!
            val before = sandboxModel(projectPath).readText()

            // A MinusExpression requires both operands; wrapping under leftExpression without filling rightExpression
            // leaves an obligatory role empty at end state.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Wrap(target = ref(plusId), concept = MINUS_EXPRESSION, role = "leftExpression"),
                    ),
                )
            }

            assertTrue(
                response.violations.any { it.operation == 0 && it.constraint == "cardinality" },
                "expected an obligatory-role violation at operation 0, got ${response.violations}",
            )
            assertEquals(before, sandboxModel(projectPath).readText(), "a blocked wrap reverts")
        }
    }

    private fun expressionStatementExpression(method: MpsNodeJson): MpsNodeJson {
        val body = childrenInRole(method, "body").single()
        val statement = childrenInRole(body, "statement").single { it.concept == EXPRESSION_STATEMENT }
        return childrenInRole(statement, "expression").single()
    }

    private fun returnExpressionOf(method: MpsNodeJson): MpsNodeJson {
        val body = childrenInRole(method, "body").single()
        val statement = childrenInRole(body, "statement").single { it.concept == RETURN_STATEMENT }
        return childrenInRole(statement, "expression").single()
    }

    private fun printlnStatementOf(mpsAccess: MpsAccess): MpsNodeJson {
        val body = childrenInRole(member(mpsAccess, "main"), "body").single()
        return childrenInRole(body, "statement").single { it.concept == EXPRESSION_STATEMENT }
    }

    // main()'s println call is `System.out.println(<argument>)`; the argument is the sole actualArgument of the call.
    private fun printlnArgumentOf(mpsAccess: MpsAccess): MpsNodeJson {
        val dot = childrenInRole(printlnStatementOf(mpsAccess), "expression").single()
        val call = childrenInRole(dot, "operation").single()
        return childrenInRole(call, "actualArgument").single()
    }

    private fun calculator(mpsAccess: MpsAccess): MpsNodeJson {
        val ref = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }
            .children!!.single { it.name == "Calculator" }.reference!!
        return mpsAccess.read { getNode(NodeTarget.NodeReference(ref)) }
    }

    private fun member(mpsAccess: MpsAccess, name: String): MpsNodeJson =
        childrenInRole(calculator(mpsAccess), "member").single { propertyValueOrNull(it, "name") == name }

    private fun ref(nodeId: String): EditTarget = EditTarget.NodeReference("$SANDBOX_MODEL/$nodeId")

    private fun nodeId(reference: String): String = reference.substringAfterLast('/')

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun allNodes(node: MpsNodeJson): List<MpsNodeJson> =
        listOf(node) + node.children.orEmpty().flatMap { allNodes(it) }

    private fun allIds(node: MpsNodeJson): Set<String> = allNodes(node).mapNotNull { it.id }.toSet()

    private fun sandboxModel(projectPath: Path): Path = projectPath.resolve(SANDBOX_MODEL_PATH)

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
        const val SANDBOX_MODEL_PATH = "solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps"

        const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
        const val MINUS_EXPRESSION = "jetbrains.mps.baseLanguage.structure.MinusExpression"
        const val INTEGER_CONSTANT = "jetbrains.mps.baseLanguage.structure.IntegerConstant"
        const val EXPRESSION_STATEMENT = "jetbrains.mps.baseLanguage.structure.ExpressionStatement"
        const val RETURN_STATEMENT = "jetbrains.mps.baseLanguage.structure.ReturnStatement"
    }
}
