package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.findUsages.InstanceLookup
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.smodel.ConceptDescendantsCache
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SearchScope
import org.jetbrains.mps.openapi.persistence.MultiStreamDataSource
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Compares participant selection, model-local lookup, and independent tree traversal under existing MPS read access.
 * Facade results are captured before model lookup or traversal can initialize caches. No models are changed or saved.
 * See the mps-api-research note `find-instances-false-negatives.md` for the MPS 2025.1.2 search contracts.
 */
internal class InstanceSearchDiagnostics(
    private val facade: (SearchScope, SAbstractConcept, Boolean) -> List<SNode> = { scope, concept, exact ->
        val collected = CollectConsumer<SNode>()
        FindUsagesFacade.getInstance().findInstances(scope, setOf(concept), exact, collected, EmptyProgressMonitor())
        collected.result.toList()
    },
    private val descendants: (SAbstractConcept) -> Set<SAbstractConcept> = {
        ConceptDescendantsCache.getInstance().getDescendants(it)
    },
    private val lookup: (SModel, Set<SAbstractConcept>) -> List<SNode> = { model, concepts ->
        val collected = mutableListOf<SNode>()
        InstanceLookup(concepts, collected::add).collectInstances(model, EmptyProgressMonitor())
        collected
    },
) {
    private val persistence = PersistenceFacade.getInstance()

    fun diagnose(
        scope: SearchScope,
        subtree: SNode?,
        concept: SAbstractConcept,
        exact: Boolean,
        filters: List<NodeFilter>,
        predicates: List<(SNode) -> Boolean>,
        limit: Int,
        expectedReference: String?,
        resolveExpected: (() -> SNode?)?,
    ): InstancesDiagnosticResponse {
        val errors = mutableListOf<String>()
        val models = scope.models.toList()
        val loadedBefore = models.associateWith { it.isLoaded }
        val changedBefore = models.associateWith { (it as? EditableSModel)?.isChanged == true }
        fun inSubtree(node: SNode): Boolean {
            if (subtree == null) return true
            var ancestor: SNode? = node
            while (ancestor != null) {
                if (ancestor == subtree) return true
                ancestor = ancestor.parent
            }
            return false
        }
        fun matches(node: SNode): Boolean =
            if (exact) node.concept == concept else node.concept.isSubConceptOf(concept)

        // Subtree production searches walk directly; the facade is an additional model probe in that case.
        val normalSubtree = subtree?.let { probe("subtree search", errors) { walk(it).filter(::matches).toList() } }
        val facadeNodes = probe("facade", errors) { facade(scope, concept, exact).filter(::inSubtree) }
        val expanded = probe("concept expansion", errors) {
            buildSet {
                add(concept)
                if (!exact) addAll(descendants(concept))
            }
        }
        val lookupNodes = mutableMapOf<SModel, List<SNode>?>()
        val modelReports = models.map { model ->
            val modelErrors = mutableListOf<String>()
            val local = expanded?.let { concepts ->
                probe("model lookup", modelErrors) { lookup(model, concepts).filter(::inSubtree) }
            }
            lookupNodes[model] = local
            val tree = probe("tree traversal", modelErrors) {
                model.rootNodes.asSequence().flatMap(::walk).filter(::inSubtree).toList()
            }
            val queryTree = expanded?.let { concepts ->
                tree?.let { nodes -> probe("query-tree matching", modelErrors) { nodes.filter { it.concept in concepts } } }
            }
            val semanticTree = tree?.let { nodes ->
                probe("semantic-tree matching", modelErrors) { nodes.filter(::matches) }
            }
            val modelFacade = facadeNodes?.filter { it.model == model }
            val differences = listOfNotNull(
                difference("participants", modelFacade, local),
                difference("model-lookup", local, queryTree),
                difference("concept-expansion", queryTree, semanticTree),
            )
            probe("model problems", modelErrors) {
                model.problems.forEach { modelErrors += "model problem: ${it.kind}: ${it.text} (${it.location})" }
            }
            val source = model.source
            val streams = probe("source streams", modelErrors) {
                if (source is MultiStreamDataSource) {
                    source.subStreams.use { it.map { stream -> stream.location }.toList() }
                } else listOf(source.location)
            }.orEmpty()
            InstanceModelDiagnosticJson(
                model = persistence.asString(model.reference),
                implementation = model.javaClass.name,
                source = source.location,
                sourceType = source.javaClass.name,
                streams = streams,
                readOnly = model.isReadOnly,
                changed = changedBefore.getValue(model),
                loadedBefore = loadedBefore.getValue(model),
                facadeCount = modelFacade?.let(::references)?.size,
                lookupCount = local?.let(::references)?.size,
                queryTreeCount = queryTree?.let(::references)?.size,
                semanticTreeCount = semanticTree?.let(::references)?.size,
                differences = differences,
                errors = modelErrors,
            )
        }
        val normal = if (subtree == null) facadeNodes else normalSubtree
        val filtered = normal?.let { nodes ->
            probe("filters", errors) { nodes.filter { node -> predicates.all { it(node) } } }
        }
        val returned = filtered?.let { if (limit > 0) it.take(limit) else it }
        val expected = expectedReference?.let { reference ->
            val errorCount = errors.size
            val expectedNode = probe("expected node resolution", errors) { resolveExpected?.invoke() }
            if (expectedNode == null) ExpectedInstanceJson(
                reference = reference, resolved = false,
                status = if (errors.size == errorCount) "unresolved" else "incomplete",
            )
            else {
                val inScope = expectedNode.model in models && inSubtree(expectedNode)
                val rejected = probe("expected node filters", errors) {
                    filters.zip(predicates).filterNot { (_, predicate) -> predicate(expectedNode) }.map { it.first }
                }
                val semanticMatch = probe("expected concept match", errors) { matches(expectedNode) }
                val status = when {
                    !inScope -> "outside-scope"
                    semanticMatch == false -> "concept-mismatch"
                    semanticMatch == null || rejected == null -> "incomplete"
                    rejected.isNotEmpty() -> "filtered"
                    normal == null || returned == null -> "incomplete"
                    expectedNode !in normal -> "search-miss"
                    expectedNode !in returned -> "result-limit"
                    else -> "returned"
                }
                ExpectedInstanceJson(
                    reference = persistence.asString(expectedNode.reference),
                    resolved = true,
                    concept = describeConcept(expectedNode.concept),
                    inScope = inScope,
                    semanticMatch = semanticMatch,
                    inExpandedConcepts = expanded?.contains(expectedNode.concept),
                    inFacade = facadeNodes?.contains(expectedNode),
                    inLookup = if (!inScope) false else lookupNodes[expectedNode.model]?.contains(expectedNode),
                    inNormalSearch = normal?.contains(expectedNode),
                    rejectedFilters = rejected.orEmpty(),
                    returned = returned?.contains(expectedNode),
                    status = status,
                )
            }
        }
        return InstancesDiagnosticResponse(
            concept = describeConcept(concept),
            exact = exact,
            filters = filters,
            scope = null,
            searchPath = if (subtree == null) "facade" else "subtree",
            mpsVersion = ApplicationInfo.getInstance().fullVersion,
            participants = persistence.findUsagesParticipants.map { it.javaClass.name },
            models = modelReports,
            normalCount = normal?.size,
            filteredCount = filtered?.size,
            returnedCount = returned?.size,
            limit = limit,
            expected = expected,
            errors = errors,
            complete = errors.isEmpty() && modelReports.all { it.errors.isEmpty() },
        )
    }

    private fun describeConcept(concept: SAbstractConcept) = InstanceConceptJson(
        name = concept.qualifiedName,
        id = MetaIdHelper.getConcept(concept).toString(),
        valid = concept.isValid,
    )

    private fun walk(root: SNode): Sequence<SNode> = sequence {
        val pending = ArrayDeque<SNode>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val node = pending.removeLast()
            yield(node)
            node.children.toList().asReversed().forEach(pending::addLast)
        }
    }

    private fun references(nodes: List<SNode>): Set<String> =
        nodes.mapTo(linkedSetOf()) { persistence.asString(it.reference) }

    private fun difference(layer: String, actual: List<SNode>?, baseline: List<SNode>?): InstanceDifferenceJson? {
        if (actual == null || baseline == null) return null
        val actualRefs = references(actual)
        val baselineRefs = references(baseline)
        val missing = baselineRefs - actualRefs
        val unexpected = actualRefs - baselineRefs
        if (missing.isEmpty() && unexpected.isEmpty()) return null
        return InstanceDifferenceJson(
            layer, missing.size, missing.sorted().take(20), unexpected.size, unexpected.sorted().take(20),
        )
    }

    private fun <T> probe(label: String, errors: MutableList<String>, action: () -> T): T? =
        try {
            action()
        } catch (exception: ProcessCanceledException) {
            throw exception
        } catch (exception: Exception) {
            errors += "$label: ${exception.javaClass.name}: ${exception.message}"
            null
        }
}
