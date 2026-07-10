package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.NodeFilter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = resolveScope(listOf("/")))
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
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE)))
        }
        val inRepository = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = resolveScope(listOf("/")))
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
            findInstances(
                CONCEPT_DECLARATION,
                exact = false,
                limit = 0,
                scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)),
            )
        }

        assertTrue(inModel.nodes.isNotEmpty(), "the structure model should hold concept declarations")
        assertTrue(
            inModel.nodes.all { it.reference.contains("($STRUCTURE_MODEL)") },
            "every model-scoped result must belong to the structure model: ${inModel.nodes}",
        )
    }

    @Test
    fun `scopes an instances search to a bare model name as a single segment`() {
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, scope = resolveScope(listOf(STRUCTURE_MODEL)))
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
            findInstances(
                LINK_DECLARATION,
                exact = false,
                limit = 0,
                scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL, "JsonObject")),
            )
        }
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(
                LINK_DECLARATION,
                exact = false,
                limit = 0,
                scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)),
            )
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
    fun `named filter keeps only instances whose name matches the pattern`() {
        val all = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0)
        }
        val named = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, filters = listOf(NodeFilter.Named("JsonObject")))
        }

        assertTrue(all.nodes.size > named.nodes.size, "the named filter should narrow the concept results")
        assertEquals(setOf("JsonObject"), named.nodes.mapNotNull { it.name }.toSet())
    }

    @Test
    fun `named filter matches camel-hump abbreviations like root-by-name`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, filters = listOf(NodeFilter.Named("JN")))
        }

        val names = payload.nodes.mapNotNull { it.name }.toSet()
        assertContains(names, "JsonNumber")
        assertContains(names, "JsonNull")
        assertTrue(names.none { it == "JsonArray" }, "JsonArray has no N hump and must not match JN: $names")
    }

    @Test
    fun `role filter keeps only instances filling that containment role`() {
        val all = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0)
        }
        val inRole = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0, filters = listOf(NodeFilter.Role("linkDeclaration")))
        }

        assertTrue(inRole.nodes.isNotEmpty(), "link declarations fill the linkDeclaration role")
        assertTrue(inRole.nodes.all { it.parent?.role == "linkDeclaration" }, "every result fills the role: ${inRole.nodes}")
        assertEquals(
            all.nodes.map { it.reference }.toSet(),
            inRole.nodes.map { it.reference }.toSet(),
            "every link declaration fills the linkDeclaration role",
        )
    }

    @Test
    fun `role filter excludes instances in other roles`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0, filters = listOf(NodeFilter.Role("noSuchRole")))
        }

        assertTrue(payload.nodes.isEmpty(), "no link declaration fills a nonexistent role: ${payload.nodes}")
    }

    @Test
    fun `role filter never matches root nodes`() {
        val roots = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0)
        }
        assertTrue(roots.nodes.isNotEmpty() && roots.nodes.all { it.type == "root" }, "concept declarations are roots")

        val filtered = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = 0, filters = listOf(NodeFilter.Role("linkDeclaration")))
        }
        assertTrue(filtered.nodes.isEmpty(), "root nodes have no containment role and never match --role: ${filtered.nodes}")
    }

    @Test
    fun `named and role filters compose by AND`() {
        val all = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(LINK_DECLARATION, exact = false, limit = 0)
        }
        val name = all.nodes.mapNotNull { it.name }.first()

        // Both filters satisfiable: keeps matching link declarations, each in the role.
        val both = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(
                LINK_DECLARATION,
                exact = false,
                limit = 0,
                filters = listOf(NodeFilter.Named(name), NodeFilter.Role("linkDeclaration")),
            )
        }
        assertTrue(both.nodes.isNotEmpty(), "a satisfiable name+role pair keeps matching link declarations")
        assertTrue(both.nodes.all { it.parent?.role == "linkDeclaration" }, "every result still fills the role")

        // Adding an unsatisfiable role empties the name matches — the role predicate ANDs in.
        val roleExcludes = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(
                LINK_DECLARATION,
                exact = false,
                limit = 0,
                filters = listOf(NodeFilter.Named(name), NodeFilter.Role("noSuchRole")),
            )
        }
        assertTrue(roleExcludes.nodes.isEmpty(), "an unsatisfiable role removes the name matches")

        // Adding an unsatisfiable name empties the role matches — the name predicate ANDs in.
        val nameExcludes = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(
                LINK_DECLARATION,
                exact = false,
                limit = 0,
                filters = listOf(NodeFilter.Named("zzzNoSuchName"), NodeFilter.Role("linkDeclaration")),
            )
        }
        assertTrue(nameExcludes.nodes.isEmpty(), "an unsatisfiable name removes the role matches")
    }

    @Test
    fun `named filter combines with a scope clause`() {
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(
                CONCEPT_DECLARATION,
                exact = false,
                scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)),
                filters = listOf(NodeFilter.Named("JsonObject")),
                limit = 0,
            )
        }

        assertEquals(setOf("JsonObject"), inModel.nodes.mapNotNull { it.name }.toSet())
        assertTrue(
            inModel.nodes.all { it.reference.contains("($STRUCTURE_MODEL)") },
            "every scoped result must belong to the structure model: ${inModel.nodes}",
        )
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
    fun `reports an unresolved concept in an unloaded language with its load diagnosis`() {
        // The fixture language is present but not compiled, so name lookup cannot see its concepts. The failure names
        // the language, its load cause, and how to inspect it further, rather than a bare "not found".
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("com.specificlanguages.json.structure.DoesNotExist", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        val message = assertNotNull(exception.message)
        assertContains(message, "its language \"com.specificlanguages.json\" is not loaded")
        assertContains(message, "NOT_BUILT")
        assertContains(message, "run 'mops diagnose module com.specificlanguages.json'")
    }

    @Test
    fun `forgives a dropped structure infix and returns the same instances`() {
        val canonical = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances(CONCEPT_DECLARATION, exact = false, limit = DEFAULT_LIMIT)
        }
        val dropped = SharedMpsEnvironment.sharedMpsAccess.read {
            findInstances("jetbrains.mps.lang.structure.ConceptDeclaration", exact = false, limit = DEFAULT_LIMIT)
        }

        assertTrue(canonical.nodes.isNotEmpty(), "fixture should hold concept declarations")
        assertEquals(canonical, dropped, "the dropped-infix form should resolve to the same concept")
    }

    @Test
    fun `suggests similar concepts in a loaded language`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("jetbrains.mps.lang.structure.structure.ConceptDeclaratn", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        val message = assertNotNull(exception.message)
        assertContains(message, "which is loaded")
        assertContains(message, "did you mean")
        assertContains(message, "ConceptDeclaration")
    }

    @Test
    fun `reports an unknown language`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("no.such.language.structure.Whatever", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertContains(assertNotNull(exception.message), "\"no.such.language\" is not a module known to this project")
    }

    @Test
    fun `refuses short-name resolution while a project language is unbuilt`() {
        // The shared fixture's own language com.specificlanguages.json is present but not built, so a concept of that
        // short name could hide there unseen. Counting matches would wrongly report uniqueness, so resolution refuses
        // and names the language to build (or asks for a qualified name). The qualified spelling is unaffected.
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("ConceptDeclaration", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, exception.code)
        val message = assertNotNull(exception.message)
        assertContains(message, "\"ConceptDeclaration\" cannot be resolved")
        assertContains(message, "com.specificlanguages.json")
        assertContains(message, "not built")
    }

    @Test
    fun `reports an ambiguous short name even while a project language is unbuilt`() {
        // `BinaryOperation` is defined by both baseLanguage and baseLanguage.collections, which are loaded here. Two
        // matches is already ambiguous — an unbuilt language could only add candidates — so this reports the ambiguity
        // rather than deferring to a build, even though com.specificlanguages.json is unbuilt in this fixture.
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("BinaryOperation", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
        val message = assertNotNull(exception.message)
        assertContains(message, "jetbrains.mps.baseLanguage.structure.BinaryOperation")
        assertContains(message, "jetbrains.mps.baseLanguage.collections.structure.BinaryOperation")
    }

    @Test
    fun `resolves short names once every project language is built`() {
        // With `exprs` built, no project language is unbuilt, so short-name counting is trustworthy and exercises all
        // three outcomes: a unique name resolves like its qualified form, an ambiguous name lists candidates, and an
        // unknown name is not found. One make covers all three.
        SharedMpsEnvironment.withProjectCopy(projectName = EXPRESSION_LANGUAGE_PROJECT) { access, _ ->
            val made = access.extra { makeModules(listOf(EXPRESSION_LANGUAGE_MODULE)) }
            assertEquals(MakeOutcome.SUCCESS, made.outcome, "building $EXPRESSION_LANGUAGE_MODULE must succeed: ${made.messages}")

            val qualified = access.read { findInstances(CONCEPT_DECLARATION, exact = false, limit = 0) }
            val short = access.read { findInstances("ConceptDeclaration", exact = false, limit = 0) }
            assertTrue(qualified.nodes.isNotEmpty(), "fixture should hold concept declarations")
            assertEquals(qualified, short, "a unique bare short name resolves to the same concept as its qualified form")

            val ambiguous = assertFailsWith<MpsRequestException> {
                access.read { findInstances("Expression", exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, ambiguous.code)
            val ambiguousMessage = assertNotNull(ambiguous.message)
            assertContains(ambiguousMessage, "exprs.structure.Expression")
            assertContains(ambiguousMessage, "jetbrains.mps.baseLanguage.structure.Expression")

            val unknown = assertFailsWith<MpsRequestException> {
                access.read { findInstances("NoSuchConceptAnywhere", exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, unknown.code)
            assertContains(assertNotNull(unknown.message), "was not found in any loaded language")
        }
    }

    @Test
    fun `reports a malformed concept name`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findInstances("com.example.structure.", exact = false, limit = DEFAULT_LIMIT)
            }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertContains(assertNotNull(exception.message), "not a well-formed concept name")
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
        const val EXPRESSION_LANGUAGE_PROJECT = "expression-language"
        const val EXPRESSION_LANGUAGE_MODULE = "exprs"
        const val DEFAULT_LIMIT = 100
    }
}
