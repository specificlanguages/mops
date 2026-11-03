package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ModelEditSemanticsTest {

    @Test
    fun `set property by node reference persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.SetProperty(
                                target = EditTarget.NodeReference(JSON_FILE_NODE_REFERENCE),
                                name = "name",
                                value = "RenamedJsonFile",
                            ),
                        ),
                    ),
                )
            }

            assertEquals(ModelEditResponse(created = emptyMap(), violations = emptyList()), response)
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE)) }
            assertEquals("RenamedJsonFile", propertyValue(node, "name"))
            assertContains(projectPath.resolve(STRUCTURE_MODEL_PATH).readText(), """value="RenamedJsonFile"""")
        }
    }

    @Test
    fun `set property by a node reference whose id uses the persisted spelling`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.SetProperty(
                                target = EditTarget.NodeReference("$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_ENCODED_NODE_ID"),
                                name = "name",
                                value = "RenamedJsonFile",
                            ),
                        ),
                    ),
                )
            }

            assertEquals(ModelEditResponse(created = emptyMap(), violations = emptyList()), response)
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE)) }
            assertEquals("RenamedJsonFile", propertyValue(node, "name"))
        }
    }

    @Test
    fun `set boolean property to true persists and reads back`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            // Create a fresh concept whose boolean `rootable` property defaults to unset (false), then set it to true.
            val created = mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(STRUCTURE_MODEL_REFERENCE),
                                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "BooleanProbe")),
                                alias = "\$c",
                            ),
                            EditOperation.SetProperty(target = EditTarget.Alias("\$c"), name = "rootable", value = "true"),
                        ),
                    ),
                )
            }

            val createdRef = created.created.getValue("c")
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(createdRef)) }
            assertEquals("true", propertyValue(node, "rootable"))
            // Persisted role for `rootable` is `19KtqR`; MPS stores boolean true as the literal string "true".
            assertContains(projectPath.resolve(STRUCTURE_MODEL_PATH).readText(), """<property role="19KtqR" value="true" />""")
        }
    }

    @Test
    fun `set enumeration property by literal presentation persists the literal`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val response = mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.SetProperty(
                                target = EditTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE),
                                name = "sourceCardinality",
                                value = "1..n",
                            ),
                        ),
                    ),
                )
            }

            assertEquals(ModelEditResponse(created = emptyMap(), violations = emptyList()), response)
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE)) }
            assertEquals("_1__n", propertyValue(node, "sourceCardinality"))
            assertContains(projectPath.resolve(STRUCTURE_MODEL_PATH).readText(), """<property role="20lbJX" value="fLJekj6/_1__n" />""")
        }
    }

    @Test
    fun `enumeration property value read from a node writes back unchanged`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = projectPath.resolve(STRUCTURE_MODEL_PATH).readText()
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE)) }
            val value = propertyValue(node, "sourceCardinality")
            assertEquals("_1", value)

            mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.SetProperty(
                                target = EditTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE),
                                name = "sourceCardinality",
                                value = "_0__n",
                            ),
                            EditOperation.SetProperty(
                                target = EditTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE),
                                name = "sourceCardinality",
                                value = value,
                            ),
                        ),
                    ),
                )
            }

            assertEquals(before, projectPath.resolve(STRUCTURE_MODEL_PATH).readText())
        }
    }

    @Test
    fun `unknown enumeration value fails and lists the literals`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = projectPath.resolve(STRUCTURE_MODEL_PATH).readText()
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            operations = listOf(
                                EditOperation.AddChild(
                                    target = EditTarget.NodeReference(JSON_FILE_NODE_REFERENCE),
                                    role = "linkDeclaration",
                                    concept = "jetbrains.mps.lang.structure.structure.LinkDeclaration",
                                    properties = listOf(MpsNodePropertyJson(name = "sourceCardinality", value = "0..1")),
                                ),
                                EditOperation.SetProperty(
                                    target = EditTarget.NodeReference(CONTENT_LINK_NODE_REFERENCE),
                                    name = "sourceCardinality",
                                    value = "bogus",
                                ),
                            ),
                        ),
                    )
                }
            }

            assertEquals(MpsErrorCode.INVALID_PROPERTY_VALUE, exception.code)
            assertContains(exception.message, "operation 1")
            assertContains(exception.message, "'bogus'")
            assertContains(exception.message, "_0__1 (0..1), _1 (1), _0__n (0..n), _1__n (1..n)")
            assertEquals(before, projectPath.resolve(STRUCTURE_MODEL_PATH).readText())
        }
    }

    @Test
    fun `clear property by model target persists`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val model = projectPath.resolve(STRUCTURE_MODEL_PATH)

            mpsAccess.write {
                modelEdit(
                    EditBatch(
                        operations = listOf(
                            EditOperation.SetProperty(
                                target = EditTarget.InModel(modelTarget = STRUCTURE_MODEL_NAME, nodeId = JSON_FILE_NODE_ID),
                                name = "conceptAlias",
                            ),
                        ),
                    ),
                )
            }

            val node = mpsAccess.read {
                getNode(NodeTarget.InModel(modelTarget = STRUCTURE_MODEL_NAME, nodeId = JSON_FILE_NODE_ID))
            }
            assertNull(propertyValueOrNull(node, "conceptAlias"))
            val persisted = model.readText()
            assertContains(persisted, """value="JsonFile"""")
            assertFalse(persisted.contains("""value="JSON File""""))
        }
    }

    @Test
    fun `failed later operation rolls back earlier property edit`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            operations = listOf(
                                EditOperation.SetProperty(
                                    target = EditTarget.NodeReference(JSON_FILE_NODE_REFERENCE),
                                    name = "name",
                                    value = "ShouldRollback",
                                ),
                                EditOperation.SetProperty(
                                    target = EditTarget.NodeReference(JSON_FILE_NODE_REFERENCE),
                                    name = "doesNotExist",
                                    value = "boom",
                                ),
                            ),
                        ),
                    )
                }
            }

            assertEquals(MpsErrorCode.PROPERTY_NOT_FOUND, exception.code)
            assertContains(exception.message, "property not found: doesNotExist")
            val node = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE)) }
            assertEquals("JsonFile", propertyValue(node, "name"))
            assertFalse(projectPath.resolve(STRUCTURE_MODEL_PATH).readText().contains("ShouldRollback"))
        }
    }

    @Test
    fun `targeting non-editable model fails`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val existing = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_FILE_NODE_REFERENCE)) }
            val extendsTarget = requireNotNull(existing.references).single { it.role == "extends" }.target
            val libraryReference = "${extendsTarget.model}/${extendsTarget.node}"

            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.write {
                    modelEdit(
                        EditBatch(
                            operations = listOf(
                                EditOperation.SetProperty(
                                    target = EditTarget.NodeReference(libraryReference),
                                    name = "name",
                                    value = "EditedBaseConcept",
                                ),
                            ),
                        ),
                    )
                }
            }

            assertEquals(MpsErrorCode.MODEL_READ_ONLY, exception.code)
            assertContains(exception.message, "not editable")
        }
    }

    private companion object {
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_NAME = "com.specificlanguages.json.structure"
        const val JSON_FILE_NODE_ID = "2110045694544566904"
        // The same node ID in the encoded spelling MPS persists in .mps files.
        const val JSON_FILE_ENCODED_NODE_ID = "1P8oQ4NaXDS"
        const val JSON_FILE_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_NODE_ID"
        const val CONTENT_LINK_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/2110045694544566910"
        const val STRUCTURE_MODEL_PATH = "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
