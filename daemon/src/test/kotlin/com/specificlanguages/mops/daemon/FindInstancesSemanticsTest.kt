package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FindInstancesResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class FindInstancesSemanticsTest {

    @Test
    fun `finds concept instances including subconcepts`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.nodes.isNotEmpty())
        assertTrue(payload.nodes.all { it.type == "root" }, "concept declarations are roots: ${payload.nodes}")
        assertTrue(
            payload.nodes.all { it.reference.startsWith("r:") },
            "every node reference should be serialized: ${payload.nodes}",
        )
        val concepts = payload.nodes.map { it.concept }.toSet()
        assertContains(concepts, CONCEPT_DECLARATION)
        assertContains(concepts, INTERFACE_CONCEPT_DECLARATION)
    }

    @Test
    fun `exact match excludes subconcept instances`() {
        // The default search in the preceding test finds subconcept instances of this abstract concept;
        // exact matching requires the direct concept, of which the fixture has none.
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(ABSTRACT_CONCEPT_DECLARATION, exact = true, limit = DEFAULT_LIMIT)
        }

        assertEquals(FindInstancesResponse(limit = DEFAULT_LIMIT, truncated = false, nodes = emptyList()), payload)
    }

    @Test
    fun `in slash searches library models beyond editable project sources`() {
        val editable = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0)
        }
        val repository = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf("/"))
        }

        val editableReferences = editable.nodes.map { it.reference }.toSet()
        val repositoryReferences = repository.nodes.map { it.reference }.toSet()

        assertTrue(editableReferences.isNotEmpty(), "fixture should hold editable concept declarations")
        assertTrue(
            repositoryReferences.size > editableReferences.size,
            "searching the whole repository should reach library instances the editable search excludes",
        )
        assertTrue(
            editableReferences.all { it in repositoryReferences },
            "editable results must remain a subset of the repository results",
        )
    }

    @Test
    fun `scopes an instances search to a module`() {
        val inModule = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf(LANGUAGE_MODULE))
        }
        val inRepository = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf("/"))
        }

        val moduleReferences = inModule.nodes.map { it.reference }.toSet()
        val repositoryReferences = inRepository.nodes.map { it.reference }.toSet()

        assertTrue(moduleReferences.isNotEmpty(), "the language module should hold concept declarations")
        assertTrue(
            repositoryReferences.size > moduleReferences.size,
            "the repository holds concept declarations beyond the one module",
        )
        assertTrue(
            moduleReferences.all { it in repositoryReferences },
            "module results must remain a subset of the repository results",
        )
    }

    @Test
    fun `scopes an instances search to a model`() {
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf(LANGUAGE_MODULE, STRUCTURE_MODEL))
        }

        assertTrue(inModel.nodes.isNotEmpty(), "the structure model should hold concept declarations")
        assertTrue(
            inModel.nodes.all { it.reference.contains("($STRUCTURE_MODEL)") },
            "every model-scoped result must belong to the structure model: ${inModel.nodes}",
        )
    }

    @Test
    fun `scopes an instances search to a node subtree returning only descendants`() {
        val inSubtree = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0, scope = listOf(LANGUAGE_MODULE, STRUCTURE_MODEL, "JsonObject"))
        }
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0, scope = listOf(LANGUAGE_MODULE, STRUCTURE_MODEL))
        }

        assertTrue(inSubtree.nodes.isNotEmpty(), "JsonObject should own at least one link declaration")
        assertTrue(
            inSubtree.nodes.all { it.parent?.name == "JsonObject" },
            "a subtree search returns only descendants of the scope node: ${inSubtree.nodes}",
        )
        assertTrue(
            inModel.nodes.size > inSubtree.nodes.size,
            "the model owns link declarations outside the JsonObject subtree",
        )
        assertTrue(
            inModel.nodes.any { it.parent?.name != "JsonObject" },
            "the model-wide search must reach link declarations owned by other roots",
        )
    }

    @Test
    fun `reports an ambiguous scope segment with candidates referencing the scope topic`() {
        val duplicateReference = "11111111-2222-4333-8444-555555555555(com.specificlanguages.json.build)"

        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val originalDescriptor = project.resolve(
                    "solutions/com.specificlanguages.json.build/com.specificlanguages.json.build.msd",
                )
                val duplicateDirectory = project.resolve("solutions/duplicate-json-build").createDirectories()
                duplicateDirectory.resolve("duplicate-json-build.msd").writeText(
                    originalDescriptor.readText().replace(
                        "84f0ad52-c7ca-45dd-99c5-9605c96bf808",
                        "11111111-2222-4333-8444-555555555555",
                    ),
                )
                val modulesXml = project.resolve(".mps/modules.xml")
                modulesXml.writeText(
                    modulesXml.readText().replace(
                        "    </projectModules>",
                        "      <modulePath path=\"\$PROJECT_DIR\$/solutions/duplicate-json-build/duplicate-json-build.msd\" folder=\"\" />\n" +
                            "    </projectModules>",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf("com.specificlanguages.json.build"))
                }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous module target com.specificlanguages.json.build")
            assertContains(exception.message, duplicateReference)
            assertContains(exception.message, "mops explain scope")
        }
    }

    @Test
    fun `reports an unresolved scope segment as not found referencing the scope topic`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = listOf("no.such.module"))
            }
        }

        assertEquals(MpsErrorCode.TARGET_NOT_FOUND, exception.code)
        assertContains(exception.message, "scope not found: no.such.module")
        assertContains(exception.message, "mops explain scope")
    }

    @Test
    fun `non-root instances carry their immediate parent`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.nodes.isNotEmpty(), "fixture should hold link declarations")
        assertTrue(payload.nodes.all { it.type == "node" }, "link declarations are children, not roots: ${payload.nodes}")
        val instance = payload.nodes.first()
        val parent = assertNotNull(instance.parent)
        assertEquals("root", parent.type)
        assertEquals("linkDeclaration", parent.role)
        assertEquals(CONCEPT_DECLARATION, parent.concept)
        // Find results carry only the immediate parent, never a nested chain.
        assertNull(parent.parent)
    }

    @Test
    fun `reports an unresolved concept as not found`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("com.specificlanguages.json.structure.DoesNotExist", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertEquals(
            "no valid MPS Concept resolved for \"com.specificlanguages.json.structure.DoesNotExist\" " +
                "(probable owning language: com.specificlanguages.json) — either the name is wrong, or its owning " +
                "language is not compiled or not loaded into the project; build the language and retry",
            exception.message,
        )
    }

    @Test
    fun `truncates results with a low limit`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 1)
        }

        assertEquals(1, payload.nodes.size)
        assertTrue(payload.truncated, "more concept declarations exist than the limit: $payload")
    }

    private companion object {
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val ABSTRACT_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"
        const val INTERFACE_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration"
        const val LINK_DECLARATION = "jetbrains.mps.lang.structure.structure.LinkDeclaration"
        const val LANGUAGE_MODULE = "com.specificlanguages.json"
        const val STRUCTURE_MODEL = "com.specificlanguages.json.structure"
        const val DEFAULT_LIMIT = 100
    }
}
