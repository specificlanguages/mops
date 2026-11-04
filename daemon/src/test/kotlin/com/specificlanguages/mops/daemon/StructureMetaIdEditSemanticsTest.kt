package com.specificlanguages.mops.daemon

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

class StructureMetaIdEditSemanticsTest {

    @Test
    fun `copyAsRoot gives a copied concept the new node id as its conceptId and reports it`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_OBJECT_REF),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val copyRef = response.created.getValue("copy")
            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(copyRef)) }
            assertEquals(copy.id, propertyValue(copy, "conceptId"))
            val link = childrenInRole(copy, "linkDeclaration").single()
            assertEquals(link.id, propertyValue(link, "linkId"))
            assertEquals(
                listOf(
                    "set conceptId of ConceptDeclaration JsonObject $copyRef to ${copy.id}",
                    "set linkId of LinkDeclaration $STRUCTURE_MODEL/${link.id} to ${link.id}",
                ),
                response.hints,
            )
        }
    }

    @Test
    fun `copying an interface concept reassigns its conceptId`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(IJSON_VALUE_REF),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val copyRef = response.created.getValue("copy")
            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(copyRef)) }
            assertEquals(copy.id, propertyValue(copy, "conceptId"))
            assertEquals(
                listOf("set conceptId of InterfaceConceptDeclaration IJsonValue $copyRef to ${copy.id}"),
                response.hints,
            )
        }
    }

    @Test
    fun `copyAsChild and a copy leaf reassign the propertyId of a copied property`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsChild(
                            target = EditTarget.NodeReference(JSON_ARRAY_REF),
                            source = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                            role = "propertyDeclaration",
                            alias = "\$childCopy",
                        ),
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_ARRAY_REF),
                            role = "linkDeclaration",
                            concept = LINK_DECLARATION,
                            properties = listOf(
                                MpsNodePropertyJson(name = "role", value = "extra"),
                                MpsNodePropertyJson(name = "linkId", value = "42"),
                            ),
                            alias = "\$link",
                        ),
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Holder")),
                            children = listOf(
                                InlineChild.Copy(
                                    role = "propertyDeclaration",
                                    source = EditTarget.NodeReference(JSON_STRING_VALUE_REF),
                                ),
                            ),
                            alias = "\$holder",
                        ),
                    ),
                )
            }

            val childCopy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("childCopy"))) }
            assertEquals(childCopy.id, propertyValue(childCopy, "propertyId"))
            val link = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("link"))) }
            assertEquals("42", propertyValue(link, "linkId"), "an explicitly set meta-id on a created node is kept")
            val holder = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("holder"))) }
            assertEquals(holder.id, propertyValue(holder, "conceptId"), "a created concept without a conceptId gets one")
            val leafCopy = childrenInRole(holder, "propertyDeclaration").single()
            assertEquals(leafCopy.id, propertyValue(leafCopy, "propertyId"))
            assertEquals(
                listOf(
                    "set propertyId of PropertyDeclaration value $STRUCTURE_MODEL/${childCopy.id} to ${childCopy.id}",
                    "set conceptId of ConceptDeclaration Holder $STRUCTURE_MODEL/${holder.id} to ${holder.id}",
                    "set propertyId of PropertyDeclaration value $STRUCTURE_MODEL/${leafCopy.id} to ${leafCopy.id}",
                ),
                response.hints,
            )
        }
    }

    @Test
    fun `an enumeration and its members get meta-ids when created and new ones when copied`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = ENUMERATION_DECLARATION,
                            properties = listOf(
                                MpsNodePropertyJson(name = "name", value = "Color"),
                                MpsNodePropertyJson(name = "datatypeId", value = "7"),
                            ),
                            children = listOf(
                                InlineChild.Fresh(
                                    role = "members",
                                    concept = ENUMERATION_MEMBER_DECLARATION,
                                    properties = listOf(MpsNodePropertyJson(name = "name", value = "red")),
                                ),
                            ),
                            alias = "\$enum",
                        ),
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.Alias("\$enum"),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val created = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("enum"))) }
            assertEquals("7", propertyValue(created, "datatypeId"))
            val createdMember = childrenInRole(created, "members").single()
            assertEquals(createdMember.id, propertyValue(createdMember, "memberId"))

            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            assertEquals(copy.id, propertyValue(copy, "datatypeId"))
            val copiedMember = childrenInRole(copy, "members").single()
            assertEquals(copiedMember.id, propertyValue(copiedMember, "memberId"))
            assertEquals(
                listOf(
                    "set memberId of EnumerationMemberDeclaration red $STRUCTURE_MODEL/${createdMember.id} to ${createdMember.id}",
                    "set datatypeId of EnumerationDeclaration Color $STRUCTURE_MODEL/${copy.id} to ${copy.id}",
                    "set memberId of EnumerationMemberDeclaration red $STRUCTURE_MODEL/${copiedMember.id} to ${copiedMember.id}",
                ),
                response.hints,
            )
        }
    }

    @Test
    fun `an explicit setProperty later in the batch overrides the assigned meta-id and drops its hint`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_OBJECT_REF),
                            alias = "\$copy",
                        ),
                        EditOperation.SetProperty(
                            target = EditTarget.Alias("\$copy"),
                            name = "conceptId",
                            value = "123456789",
                        ),
                    ),
                )
            }

            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            assertEquals("123456789", propertyValue(copy, "conceptId"))
            val link = childrenInRole(copy, "linkDeclaration").single()
            assertEquals(
                listOf("set linkId of LinkDeclaration $STRUCTURE_MODEL/${link.id} to ${link.id}"),
                response.hints,
            )
        }
    }

    @Test
    fun `a declaration deleted later in the batch gets no hint and a hint names the final node name`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_OBJECT_REF),
                            alias = "\$deleted",
                        ),
                        EditOperation.Delete(target = EditTarget.Alias("\$deleted")),
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(IJSON_VALUE_REF),
                            alias = "\$renamed",
                        ),
                        EditOperation.SetProperty(
                            target = EditTarget.Alias("\$renamed"),
                            name = "name",
                            value = "IRenamed",
                        ),
                    ),
                )
            }

            val renamedRef = response.created.getValue("renamed")
            val renamed = mpsAccess.read { getNode(NodeTarget.NodeReference(renamedRef)) }
            assertEquals(
                listOf("set conceptId of InterfaceConceptDeclaration IRenamed $renamedRef to ${renamed.id}"),
                response.hints,
            )
        }
    }

    @Test
    fun `a blocked batch reports no hints`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_OBJECT_REF),
                        ),
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference(JSON_FILE_REF),
                            role = "propertyDeclaration",
                            concept = LINK_DECLARATION,
                        ),
                    ),
                )
            }

            assertTrue(response.violations.isNotEmpty())
            assertEquals(emptyList(), response.hints)
        }
    }

    @Test
    fun `a copy gets a conceptId different from its source's`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.NodeReference(JSON_OBJECT_REF),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val source = mpsAccess.read { getNode(NodeTarget.NodeReference(JSON_OBJECT_REF)) }
            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            val sourceConceptId = propertyValue(source, "conceptId")
            assertTrue(sourceConceptId.isNotEmpty(), "the fixture's JsonObject has a conceptId")
            assertNotEquals(sourceConceptId, propertyValue(copy, "conceptId"))
        }
    }

    @Test
    fun `primitive and constrained datatypes get a datatypeId when created and a new one when copied`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = PRIMITIVE_DATA_TYPE_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "number")),
                            alias = "\$primitive",
                        ),
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONSTRAINED_DATA_TYPE_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "digits")),
                            alias = "\$constrained",
                        ),
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            source = EditTarget.Alias("\$constrained"),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val nodes = listOf("primitive", "constrained", "copy").map { alias ->
                mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue(alias))) }
            }
            nodes.forEach { assertEquals(it.id, propertyValue(it, "datatypeId")) }
            val (primitive, constrained, copy) = nodes
            assertEquals(
                listOf(
                    "set datatypeId of PrimitiveDataTypeDeclaration number $STRUCTURE_MODEL/${primitive.id} to ${primitive.id}",
                    "set datatypeId of ConstrainedDataTypeDeclaration digits $STRUCTURE_MODEL/${constrained.id} to ${constrained.id}",
                    "set datatypeId of ConstrainedDataTypeDeclaration digits $STRUCTURE_MODEL/${copy.id} to ${copy.id}",
                ),
                response.hints,
            )
        }
    }

    @Test
    fun `replace with a copy leaf reassigns the meta-id of the copy`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, _ ->
            val response = mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.Replace(
                            target = EditTarget.NodeReference(JSON_ARRAY_ITEMS_LINK_REF),
                            with = InlineChild.Copy(role = null, source = EditTarget.NodeReference(JSON_ARRAY_ITEMS_LINK_REF)),
                            alias = "\$copy",
                        ),
                    ),
                )
            }

            val copy = mpsAccess.read { getNode(NodeTarget.NodeReference(response.created.getValue("copy"))) }
            assertEquals(copy.id, propertyValue(copy, "linkId"))
            assertEquals(
                listOf("set linkId of LinkDeclaration $STRUCTURE_MODEL/${copy.id} to ${copy.id}"),
                response.hints,
            )
        }
    }

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun childrenInRole(node: MpsNodeJson, role: String): List<MpsNodeJson> =
        node.children.orEmpty().filter { it.role == role }

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"

        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
        const val ENUMERATION_DECLARATION = "jetbrains.mps.lang.structure.structure.EnumerationDeclaration"
        const val ENUMERATION_MEMBER_DECLARATION = "jetbrains.mps.lang.structure.structure.EnumerationMemberDeclaration"
        const val PRIMITIVE_DATA_TYPE_DECLARATION = "jetbrains.mps.lang.structure.structure.PrimitiveDataTypeDeclaration"
        const val CONSTRAINED_DATA_TYPE_DECLARATION = "jetbrains.mps.lang.structure.structure.ConstrainedDataTypeDeclaration"

        const val JSON_OBJECT_REF = "$STRUCTURE_MODEL/2110045694544567020"
        const val IJSON_VALUE_REF = "$STRUCTURE_MODEL/2110045694544566909"
        const val JSON_ARRAY_REF = "$STRUCTURE_MODEL/2110045694544569357"
        const val JSON_STRING_VALUE_REF = "$STRUCTURE_MODEL/2110045694544569338"
        const val JSON_FILE_REF = "$STRUCTURE_MODEL/2110045694544566904"

        // JsonArray's `items` LinkDeclaration child.
        const val JSON_ARRAY_ITEMS_LINK_REF = "$STRUCTURE_MODEL/2110045694544569360"
    }
}
