package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.InlineReference
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Edit batches set references through MPS reference setter hooks. `jetbrains.mps.lang.behavior`'s
 * `ConceptMethodDeclaration.overriddenMethod` handler replaces the method's parameters with copies of the overridden
 * method's parameters and copies its name.
 */
class ReferenceSetterHookEditSemanticsTest {

    @Test
    fun `setReference runs the reference set handler`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                advisoryEdit(
                    EditBatch(
                        behaviorWithBaseMethod() + listOf(
                            EditOperation.AddChild(
                                target = EditTarget.Alias("\$behavior"),
                                role = "method",
                                concept = CONCEPT_METHOD,
                                properties = listOf(MpsNodePropertyJson("name", "derived")),
                                children = listOf(parameter("fromBatch")),
                                alias = "\$derived",
                            ),
                            EditOperation.SetReference(
                                target = EditTarget.Alias("\$derived"),
                                role = "overriddenMethod",
                                to = EditTarget.Alias("\$base"),
                            ),
                        ),
                    ),
                )
            }

            val derived = readNode(mpsAccess, response.created.getValue("derived"))
            assertEquals("base", derived.property("name"))
            assertEquals(listOf("fromBase"), derived.parameterNames())
        }
    }

    @Test
    fun `inline references are set after the node's properties and children, so the handler sees the built node`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                advisoryEdit(
                    EditBatch(
                        behaviorWithBaseMethod() + EditOperation.AddChild(
                            target = EditTarget.Alias("\$behavior"),
                            role = "method",
                            concept = CONCEPT_METHOD,
                            properties = listOf(MpsNodePropertyJson("name", "derived")),
                            references = listOf(InlineReference(role = "overriddenMethod", to = EditTarget.Alias("\$base"))),
                            children = listOf(parameter("fromBatch")),
                            alias = "\$derived",
                        ),
                    ),
                )
            }

            val derived = readNode(mpsAccess, response.created.getValue("derived"))
            assertEquals("base", derived.property("name"))
            assertEquals(listOf("fromBase"), derived.parameterNames())
        }
    }

    private fun behaviorWithBaseMethod(): List<EditOperation> = listOf(
        EditOperation.AddRoot(
            model = ModelDestination(BEHAVIOR_MODEL),
            concept = "jetbrains.mps.lang.behavior.structure.ConceptBehavior",
            alias = "\$behavior",
        ),
        EditOperation.AddChild(
            target = EditTarget.Alias("\$behavior"),
            role = "method",
            concept = CONCEPT_METHOD,
            properties = listOf(MpsNodePropertyJson("name", "base")),
            children = listOf(parameter("fromBase")),
            alias = "\$base",
        ),
    )

    private fun parameter(name: String) = InlineChild.Fresh(
        role = "parameter",
        concept = "jetbrains.mps.baseLanguage.structure.ParameterDeclaration",
        properties = listOf(MpsNodePropertyJson("name", name)),
    )

    private fun readNode(mpsAccess: MpsAccess, reference: String): MpsNodeJson =
        mpsAccess.read { getNode(NodeTarget.NodeReference(reference)) }

    private fun MpsNodeJson.property(name: String): String? = properties?.single { it.name == name }?.value

    private fun MpsNodeJson.parameterNames(): List<String?> =
        children.orEmpty().filter { it.role == "parameter" }.map { it.property("name") }

    private companion object {
        const val BEHAVIOR_MODEL = "r:0e3ed68a-e841-4ab1-bafa-60852d5e8891(com.specificlanguages.json.behavior)"
        const val CONCEPT_METHOD = "jetbrains.mps.lang.behavior.structure.ConceptMethodDeclaration"
    }
}
