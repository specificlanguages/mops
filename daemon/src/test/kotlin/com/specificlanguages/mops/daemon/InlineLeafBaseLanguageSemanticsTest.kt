package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Move/Copy Leaf semantics exercised against the baseLanguage sandbox, whose Calculator class carries a real inbound
 * Reference (a method call in `main` targets the `add` method) and internal References (each method body reads its
 * parameters), so identity preservation and copy rewiring can be observed on live references rather than inferred.
 *
 * Node ids are discovered by navigation rather than hard-coded, since they are assigned by MPS on load.
 */
class InlineLeafBaseLanguageSemanticsTest {

    @Test
    fun `a move leaf adopting a referenced method keeps the inbound call resolving`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val addId = member(mpsAccess, "add").id!!

            // Move the `add` method out of Calculator into a brand-new class, identity-preserving.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(SANDBOX_MODEL),
                            concept = CLASS_CONCEPT,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Calculator2")),
                            children = listOf(
                                InlineChild.Move(role = "member", source = ref(addId)),
                            ),
                            alias = "\$c2",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            // The moved method kept its identity under the new class, and left Calculator.
            val calculator2 = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("c2"))) }
            assertEquals("add", propertyValueOrNull(childrenInRole(calculator2, "member").single { it.id == addId }, "name"))
            assertTrue(childrenInRole(calculator(mpsAccess), "member").none { it.id == addId })

            // The inbound call in main() still resolves to it: the Reference stored the node id, which is unchanged.
            val calledFromMain = references(member(mpsAccess, "main"))
                .single { it.role == "baseMethodDeclaration" && it.target.node == addId }
            assertTrue(calledFromMain.target.resolved, "the inbound method call must still resolve after the move")
        }
    }

    @Test
    fun `a copy leaf clones a method with fresh ids and rewires its parameter reads to the copy`() {
        SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { mpsAccess, _ ->
            val add = member(mpsAccess, "add")
            val addId = add.id!!
            val originalParameterIds = childrenInRole(add, "parameter").mapNotNull { it.id }.toSet()

            // Clone `add` into a new class via a Copy Leaf.
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(SANDBOX_MODEL),
                            concept = CLASS_CONCEPT,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "CalculatorCopy")),
                            children = listOf(
                                InlineChild.Copy(role = "member", source = ref(addId)),
                            ),
                            alias = "\$copy",
                        ),
                    ),
                )
            }
            assertTrue(response.violations.isEmpty(), "expected no violations, got ${response.violations}")

            val copyClass = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            val clone = childrenInRole(copyClass, "member").single()
            assertEquals("add", propertyValueOrNull(clone, "name"))
            assertNotEquals(addId, clone.id, "the clone must receive a fresh node id")

            val cloneParameterIds = childrenInRole(clone, "parameter").mapNotNull { it.id }.toSet()
            assertEquals(2, cloneParameterIds.size)
            assertTrue(originalParameterIds.none { it in cloneParameterIds }, "copied parameters must be fresh")

            // The clone's parameter reads were rewired to the clone's own parameters, not the original's.
            val cloneReads = references(clone).filter { it.role == "variableDeclaration" }.mapNotNull { it.target.node }
            assertEquals(2, cloneReads.size, "add reads both parameters")
            assertTrue(cloneReads.all { it in cloneParameterIds }, "reads must resolve within the copy: $cloneReads")

            // The original method is untouched: its reads still target its own parameters.
            val originalReads = references(member(mpsAccess, "add"))
                .filter { it.role == "variableDeclaration" }.mapNotNull { it.target.node }.toSet()
            assertEquals(originalParameterIds, originalReads)
        }
    }

    private fun calculator(mpsAccess: MpsAccess): MpsNodeJson {
        val ref = mpsAccess.read { list(listOf(SANDBOX_MODEL), depth = 1) }
            .children!!.single { it.name == "Calculator" }.reference!!
        return mpsAccess.read { getNode(NodeTarget.NodeReference(ref)) }
    }

    private fun member(mpsAccess: MpsAccess, name: String): MpsNodeJson =
        childrenInRole(calculator(mpsAccess), "member").single { propertyValueOrNull(it, "name") == name }

    private fun ref(nodeId: String): EditTarget = EditTarget.NodeReference("$SANDBOX_MODEL/$nodeId")

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private fun references(node: MpsNodeJson): List<com.specificlanguages.mops.protocol.MpsNodeReferenceJson> =
        node.references.orEmpty() + node.children.orEmpty().flatMap { references(it) }

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_MODEL = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
        const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
    }
}
