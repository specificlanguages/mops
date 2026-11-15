package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.ChildPosition
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

    @Test
    fun `a replacement's inline references are set after it takes the replaced node's place`() {
        // lang.typesystem's ConceptReference.concept handler renames the parent InferenceRule only when the reference
        // node is already under it.
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                advisoryEdit(
                    EditBatch(
                        listOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(JSON_BEHAVIOR_MODEL),
                                concept = "jetbrains.mps.lang.typesystem.structure.InferenceRule",
                                properties = listOf(MpsNodePropertyJson("name", "rule")),
                                children = listOf(InlineChild.Fresh(role = "applicableNode", concept = CONCEPT_REFERENCE)),
                                alias = "\$rule",
                            ),
                            EditOperation.Replace(
                                target = EditTarget.RelativeAlias("\$rule", listOf(EditTarget.PathStep("applicableNode", ChildPosition.Only))),
                                with = InlineChild.Fresh(
                                    concept = CONCEPT_REFERENCE,
                                    references = listOf(
                                        InlineReference(role = "concept", to = EditTarget.NodeReference(JSON_FILE_CONCEPT)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }

            val rule = readNode(mpsAccess, response.created.getValue("rule"))
            assertEquals("typeof_JsonFile", rule.property("name"))
        }
    }

    @Test
    fun `a wrapper's inline references are set after it adopts the wrapped node`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                advisoryEdit(
                    EditBatch(
                        behaviorWithBaseMethod() + listOf(
                            EditOperation.AddChild(
                                target = EditTarget.Alias("\$behavior"),
                                role = "method",
                                concept = CONCEPT_METHOD,
                                properties = listOf(MpsNodePropertyJson("name", "other")),
                                alias = "\$other",
                            ),
                            EditOperation.Wrap(
                                target = EditTarget.Alias("\$other"),
                                concept = CONCEPT_METHOD,
                                role = "parameter",
                                references = listOf(InlineReference(role = "overriddenMethod", to = EditTarget.Alias("\$base"))),
                                alias = "\$wrapper",
                            ),
                        ),
                    ),
                )
            }

            // The handler replaces every parameter, including the wrapped node, as a later setReference would.
            val wrapper = readNode(mpsAccess, response.created.getValue("wrapper"))
            assertEquals(listOf("fromBase"), wrapper.parameterNames())
        }
    }

    private fun behaviorWithBaseMethod(): List<EditOperation> = listOf(
        EditOperation.AddRoot(
            model = ModelDestination(JSON_BEHAVIOR_MODEL),
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
        const val JSON_FILE_CONCEPT = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        const val CONCEPT_REFERENCE = "jetbrains.mps.lang.typesystem.structure.ConceptReference"
        const val CONCEPT_METHOD = "jetbrains.mps.lang.behavior.structure.ConceptMethodDeclaration"
    }
}
