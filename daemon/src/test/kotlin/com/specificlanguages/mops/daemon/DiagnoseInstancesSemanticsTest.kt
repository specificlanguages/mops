package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.NodeFilter
import jetbrains.mps.ide.findusages.model.scopes.ModelsScope
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import kotlin.test.*

class DiagnoseInstancesSemanticsTest {
    @Test
    fun `healthy search agrees across all four layers`() {
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            diagnoseInstances(BASE, scope = resolveScope(listOf(MODEL)), limit = 1)
        }
        assertTrue(result.complete, result.toString())
        val model = result.models.single()
        assertTrue(requireNotNull(model.semanticTreeCount) > 1)
        assertEquals(model.semanticTreeCount, model.facadeCount)
        assertEquals(model.semanticTreeCount, model.lookupCount)
        assertEquals(model.semanticTreeCount, model.queryTreeCount)
        assertEquals(emptyList(), model.differences)
        assertEquals(1, result.returnedCount)
        assertTrue(requireNotNull(result.normalCount) > 1)
    }

    @Test
    fun `expected node distinguishes filters truncation scope and exact matching`() {
        val access = SharedMpsEnvironment.sharedMpsAccess
        access.read {
            val scope = resolveScope(listOf(MODEL))
            val nodes = findInstances(CONCEPT, exact = true, scope = scope, limit = 0).nodes
            val target = nodes.last().reference
            val filtered = diagnoseInstances(CONCEPT, true, scope, listOf(NodeFilter.Role("missing")), 0, target)
            assertEquals("filtered", filtered.expected?.status)
            assertEquals(listOf(NodeFilter.Role("missing")), filtered.expected?.rejectedFilters)
            val limited = diagnoseInstances(CONCEPT, true, scope, limit = 1, expect = target)
            assertEquals("result-limit", limited.expected?.status)
            val outside = diagnoseInstances(CONCEPT, true, resolveScope(listOf(nodes.first().reference)), expect = target)
            assertEquals("outside-scope", outside.expected?.status)
            assertEquals("subtree", outside.searchPath)
            val exactBase = diagnoseInstances(BASE, true, scope, expect = target)
            assertEquals("concept-mismatch", exactBase.expected?.status)
            val found = diagnoseInstances(BASE, false, scope, limit = 0, expect = target)
            assertEquals("returned", found.expected?.status)
            val unresolved = diagnoseInstances(CONCEPT, true, scope, expect = target.substringBeforeLast('/') + "/999999999")
            assertEquals("unresolved", unresolved.expected?.status)
        }
    }

    @Test
    fun `identifies participant omissions independently of model lookup`() = withModel { model, concept ->
        val result = InstanceSearchDiagnostics(facade = { _, _, _ -> emptyList() }).diagnose(
            ModelsScope(model), null, concept, true, emptyList(), emptyList(), 100, null, null,
        )
        assertTrue(result.complete, result.toString())
        val report = result.models.single()
        assertEquals(0, report.facadeCount)
        val difference = report.differences.single()
        assertEquals("participants", difference.layer)
        assertEquals(report.lookupCount, difference.missingCount)
        assertTrue(difference.missing.isNotEmpty())
        assertEquals(0, difference.unexpectedCount)
    }

    @Test
    fun `identifies model finder omissions against independent traversal`() = withModel { model, concept ->
        val result = InstanceSearchDiagnostics(lookup = { _, _ -> emptyList() }).diagnose(
            ModelsScope(model), null, concept, true, emptyList(), emptyList(), 0, null, null,
        )
        val difference = result.models.single().differences.single { it.layer == "model-lookup" }
        assertTrue(difference.missingCount > 0)
        assertEquals(0, difference.unexpectedCount)
    }

    @Test
    fun `identifies missing descendants even with valid base concept`() = withModel { model, _ ->
        val base = jetbrains.mps.smodel.language.ConceptRegistry.getInstance().getConceptByName(BASE)
        val result = InstanceSearchDiagnostics(descendants = { emptySet() }).diagnose(
            ModelsScope(model), null, base, false, emptyList(), emptyList(), 0, null, null,
        )
        val difference = result.models.single().differences.single { it.layer == "concept-expansion" }
        assertTrue(difference.missingCount > 0)
    }

    @Test
    fun `equal counts with different nodes remain a discrepancy`() = withModel { model, concept ->
        val nodes = model.rootNodes.filter { it.concept == concept }
        val persistence = PersistenceFacade.getInstance()
        val result = InstanceSearchDiagnostics(
            facade = { _, _, _ -> listOf(nodes[0]) }, lookup = { _, _ -> listOf(nodes[1]) },
        ).diagnose(ModelsScope(model), null, concept, true, emptyList(), emptyList(), 0, null, null)
        val difference = result.models.single().differences.single { it.layer == "participants" }
        assertEquals(listOf(persistence.asString(nodes[1].reference)), difference.missing)
        assertEquals(listOf(persistence.asString(nodes[0].reference)), difference.unexpected)
    }

    @Test
    fun `failed probe is incomplete and never becomes an empty successful result`() = withModel { model, concept ->
        val result = InstanceSearchDiagnostics(lookup = { _, _ -> error("lookup failed") }).diagnose(
            ModelsScope(model), null, concept, true, emptyList(), emptyList(), 0, null, null,
        )
        assertFalse(result.complete)
        val report = result.models.single()
        assertNull(report.lookupCount)
        assertTrue(report.errors.single().contains("lookup failed"))
        assertTrue(report.differences.none { it.layer == "model-lookup" || it.layer == "participants" })
        assertTrue(requireNotNull(report.semanticTreeCount) > 0)
    }

    @Test
    fun `captures facade before model lookup and resolves expectation last`() = withModel { model, concept ->
        val stages = mutableListOf<String>()
        val node = model.rootNodes.first { it.concept == concept }
        val reference = PersistenceFacade.getInstance().asString(node.reference)
        val diagnostics = InstanceSearchDiagnostics(
            facade = { _, _, _ -> stages += "facade"; listOf(node) },
            lookup = { _, _ -> stages += "lookup"; listOf(node) },
        )
        val result = diagnostics.diagnose(
            ModelsScope(model), null, concept, true, emptyList(), emptyList(), 0, reference,
            { stages += "expected"; node },
        )
        assertEquals(listOf("facade", "lookup", "expected"), stages)
        assertEquals("returned", result.expected?.status)
    }

    @Test
    fun `cancellation is propagated instead of reported as missing instances`() = withModel { model, concept ->
        val diagnostics = InstanceSearchDiagnostics(facade = { _, _, _ ->
            throw com.intellij.openapi.progress.ProcessCanceledException()
        })
        assertFailsWith<com.intellij.openapi.progress.ProcessCanceledException> {
            diagnostics.diagnose(ModelsScope(model), null, concept, true, emptyList(), emptyList(), 0, null, null)
        }
    }

    @Test
    fun `unparseable expected reference is incomplete rather than unresolved`() {
        val result = SharedMpsEnvironment.sharedMpsAccess.read {
            diagnoseInstances(CONCEPT, scope = resolveScope(listOf(MODEL)), expect = "not-a-node-reference")
        }
        assertFalse(result.complete)
        assertEquals("incomplete", result.expected?.status)
        assertTrue(result.errors.single().startsWith("expected node resolution:"))
    }

    private fun withModel(action: (org.jetbrains.mps.openapi.model.SModel, org.jetbrains.mps.openapi.language.SAbstractConcept) -> Unit) {
        val access = SharedMpsEnvironment.sharedMpsAccess as JetBrainsMpsAccess
        access.read {
            val model = access.project.repository.modules.flatMap { it.models }.single { it.name.value == MODEL }
            val concept = ConceptResolver(access.project).resolve(CONCEPT)
            action(model, concept)
        }
    }

    private companion object {
        const val MODEL = "com.specificlanguages.json.structure"
        const val CONCEPT = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val BASE = "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"
    }
}
