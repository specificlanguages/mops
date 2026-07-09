package com.specificlanguages.mops.daemon

import com.intellij.psi.codeStyle.NameUtil
import com.specificlanguages.mops.daemon.core.*
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.ide.findusages.model.scopes.ModelsScope
import jetbrains.mps.ide.findusages.model.scopes.ModulesScope
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.EditableFilteringScope
import jetbrains.mps.project.GlobalScope
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.model.*
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SearchScope
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class JetBrainsMpsAccess(
    private val project: Project,
    logger: DaemonLogger,
    private val mpsListExporter: MpsListExporter = MpsListExporter(),
    private val jsonNodeExporter: JsonNodeExporter = JsonNodeExporter(),
    private val modelNodeResolver: ModelNodeResolver = ModelNodeResolver(logger),
    private val editBatchExecutor: EditBatchExecutor = EditBatchExecutor(modelNodeResolver),
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
    private val writeTransaction: WriteTransaction = WriteTransaction(),
) : MpsAccess {
    // An MpsRequestException is an expected client error, not a defect. Capture it inside the model action and rethrow
    // it on the calling thread once the action has returned: if it escaped the action directly, MPS's ActionDispatcher
    // would log it as a SEVERE "Action dispatch failed" before rethrowing, drowning the daemon log in stack traces for
    // routine failures like an unresolved concept or a missing target.
    override fun <T> read(block: MpsRead.() -> T): T =
        project.modelAccess.computeReadAction {
            captureRequestErrors { JetBrainsMpsRead().block() }
        }.getOrThrow()

    override fun <T> write(block: MpsWrite.() -> T): T =
        writeTransaction.run(project) {
            captureRequestErrors { JetBrainsMpsWrite(this).block() }
        }.getOrThrow()

    private open inner class JetBrainsMpsRead : MpsRead {
        override fun list(target: List<String>?, depth: Int): MpsListEntryJson =
            when (target) {
                null -> mpsListExporter.exportProject(project, depth)
                listOf("/") -> mpsListExporter.exportRepository(project.repository, depth)
                else -> {
                    val root = when (val resolution = resolveListTarget(target)) {
                        is ListTargetResolution.Found -> resolution.target
                        is ListTargetResolution.Ambiguous -> throw MpsRequestException(
                            code = MpsErrorCode.AMBIGUOUS_TARGET,
                            message = resolution.message,
                        )
                        ListTargetResolution.Missing -> throw MpsRequestException(
                            code = MpsErrorCode.TARGET_NOT_FOUND,
                            message = "target not found: ${target.joinToString(" ")}",
                        )
                    }
                    when (root) {
                        is ListTarget.Module -> mpsListExporter.exportModule(root.module, depth)
                        is ListTarget.Model -> mpsListExporter.exportModel(root.model, depth)
                        is ListTarget.RootNode -> mpsListExporter.exportRoot(root.node, depth)
                        is ListTarget.Node -> mpsListExporter.exportNode(root.node, depth)
                    }
                }
            }

        override fun getNode(target: NodeTarget, ancestry: Boolean): MpsNodeJson =
            jsonNodeExporter.export(resolveNode(target), ancestry)

        override fun findUsages(target: NodeTarget, limit: Int, scope: List<String>?): FindUsagesResponse {
            val node = resolveNode(target)
            val resolvedScope = resolveSearchScope(scope)
            val collected = CollectConsumer<SReference>()
            FindUsagesFacade.getInstance()
                .findUsages(resolvedScope.searchScope, setOf(node), collected, EmptyProgressMonitor())

            val references = resolvedScope.retainWithinSubtree(collected.result) { it.sourceNode }
            val selected = if (limit > 0) references.take(limit) else references

            return FindUsagesResponse(
                limit = limit,
                truncated = selected.size < references.size,
                usages = selected.map {
                    MpsNodeUsageJson(role = it.link.name, owner = nodeSummary(it.sourceNode))
                },
            )
        }

        override fun findInstances(concept: String, exact: Boolean, limit: Int, scope: List<String>?): FindInstancesResponse {
            val mpsConcept = ConceptRegistry.getInstance().getConceptByName(concept)
            if (!mpsConcept.isValid) {
                throw MpsRequestException(
                    code = MpsErrorCode.CONCEPT_NOT_FOUND,
                    message = ConceptValidityGuard.messageForUnresolvedConcept(concept),
                )
            }

            val resolvedScope = resolveSearchScope(scope)
            val collected = CollectConsumer<SNode>()
            FindUsagesFacade.getInstance()
                .findInstances(resolvedScope.searchScope, setOf(mpsConcept), exact, collected, EmptyProgressMonitor())

            val instances = resolvedScope.retainWithinSubtree(collected.result) { it }
            val selected = if (limit > 0) instances.take(limit) else instances

            return FindInstancesResponse(
                limit = limit,
                truncated = selected.size < instances.size,
                nodes = selected.map { nodeSummary(it) },
            )
        }

        override fun findByName(pattern: String, limit: Int, all: Boolean): FindByNameResponse {
            // The leading '*' reproduces MPS Go-to-Node's "search in any place": the pattern matches anywhere within a
            // name, not only as a prefix. The MinusculeMatcher then supports camel-hump and '*' wildcards, and
            // matchingDegree ranks prefix and contiguous matches above scattered middle matches.
            val matcher = NameUtil.buildMatcher("*$pattern")
                .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
                .build()

            val scope = searchScope(all)
            val matches = mutableListOf<Pair<SNode, Int>>()
            for (model in scope.models) {
                for (root in model.rootNodes) {
                    val name = nodeName(root) ?: continue
                    if (matcher.matches(name)) {
                        matches.add(root to matcher.matchingDegree(name))
                    }
                }
            }

            val ordered = matches
                .sortedWith(
                    compareByDescending<Pair<SNode, Int>> { it.second }
                        .thenBy { nodeName(it.first) }
                        .thenBy { persistence.asString(it.first.reference) },
                )
                .map { it.first }
            val selected = if (limit > 0) ordered.take(limit) else ordered

            return FindByNameResponse(
                limit = limit,
                truncated = selected.size < ordered.size,
                nodes = selected.map { nodeSummary(it) },
            )
        }

        override fun diagnoseModules(): ModulesDiagnosticsResponse =
            ModuleLoadDiagnostics(project).diagnoseModules()

        override fun diagnoseModule(module: String): ModuleDiagnosticResponse =
            ModuleLoadDiagnostics(project).diagnoseModule(module)

        // Editable Project Sources are the default find scope; `all` widens to the whole repository, including the
        // read-only library and stub models the editable filter otherwise excludes.
        private fun searchScope(all: Boolean): SearchScope =
            if (all) GlobalScope(project.repository) else EditableFilteringScope(project.scope)

        protected fun nodeSummary(node: SNode): MpsNodeSummaryJson =
            MpsNodeSummaryJson(
                type = if (node.parent == null) "root" else "node",
                name = nodeName(node),
                concept = node.concept.qualifiedName,
                conceptValid = node.concept.isValid,
                reference = persistence.asString(node.reference),
                parent = nodeParent(node, fullChain = false, persistence),
            )

        private fun resolveNode(target: NodeTarget): SNode =
            modelNodeResolver.findNode(project, target)
                ?: throw MpsRequestException(code = MpsErrorCode.NODE_NOT_FOUND, message = "node not found")
    }

    private inner class JetBrainsMpsWrite(
        private val writeScope: WriteTransaction.WriteScope,
    ) : JetBrainsMpsRead(), MpsWrite {
        override fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement): ModelEditResponse =
            editBatchExecutor.apply(project, batch, writeScope, constraints)

        override fun resave(modelTarget: String) {
            val model = modelNodeResolver.findModel(project, modelTarget)
                ?: throw MpsRequestException(
                    code = MpsErrorCode.MODEL_NOT_FOUND,
                    message = "model not found: $modelTarget",
                )
            val editable = writeScope.asEditable(model)
                ?: throw MpsRequestException(
                    code = MpsErrorCode.MODEL_READ_ONLY,
                    message = "model is not editable: ${model.name.longName}",
                )

            when (val outcome = writeScope.saveWithResolveInfo(listOf(editable))) {
                SaveOutcome.Saved -> Unit
                is SaveOutcome.SaveFailed -> throw MpsRequestException(
                    code = MpsErrorCode.SAVE_FAILED,
                    message = "model save failed for ${outcome.model.name.longName}: ${outcome.result}",
                )
            }
        }
    }

    // Maps an optional `in` clause to the MPS Search Scope a find runs over. The default (no clause) is Editable
    // Project Sources; `["/"]` is the whole repository; any other segment list resolves through the same navigation
    // grammar `list` uses and is searched exhaustively. A repository, module, or model scope maps directly to an MPS
    // search scope; a Root Node or nested-node scope has no MPS scope of its own, so it runs over the containing model
    // and post-filters results to that node's subtree.
    private fun resolveSearchScope(scope: List<String>?): ScopeResolution =
        when (scope) {
            null -> ScopeResolution(EditableFilteringScope(project.scope), containmentRoot = null)
            listOf("/") -> ScopeResolution(GlobalScope(project.repository), containmentRoot = null)
            else -> when (val resolution = resolveListTarget(scope)) {
                is ListTargetResolution.Found -> scopeForTarget(resolution.target)
                is ListTargetResolution.Ambiguous -> throw MpsRequestException(
                    code = MpsErrorCode.AMBIGUOUS_TARGET,
                    message = "${resolution.message}\nsee: mops explain scope",
                )
                ListTargetResolution.Missing -> throw MpsRequestException(
                    code = MpsErrorCode.TARGET_NOT_FOUND,
                    message = "scope not found: ${scope.joinToString(" ")} — see: mops explain scope",
                )
            }
        }

    private fun scopeForTarget(target: ListTarget): ScopeResolution =
        when (target) {
            is ListTarget.Module -> ScopeResolution(ModulesScope(listOf(target.module)), containmentRoot = null)
            is ListTarget.Model -> ScopeResolution(ModelsScope(listOf(target.model)), containmentRoot = null)
            is ListTarget.RootNode -> ScopeResolution(modelScopeOf(target.node), containmentRoot = target.node)
            is ListTarget.Node -> ScopeResolution(modelScopeOf(target.node), containmentRoot = target.node)
        }

    private fun modelScopeOf(node: SNode): SearchScope = ModelsScope(listOfNotNull(node.model))

    private fun resolveListTarget(target: List<String>): ListTargetResolution {
        if (target.isEmpty()) {
            return ListTargetResolution.Missing
        }

        if (target.size == 1) {
            resolveNodeReference(target.single())?.let {
                return ListTargetResolution.Found(listTargetForNode(it))
            }
        }

        resolveModelReference(target[0])?.let {
            return resolveModelPathTarget(it, target.drop(1))
        }

        if (target.size == 1) {
            return resolveProjectModuleTarget(target.single())
        }

        val module = when (val resolution = resolveProjectModuleTarget(target[0])) {
            is ListTargetResolution.Found -> (resolution.target as ListTarget.Module).module
            is ListTargetResolution.Ambiguous -> return resolution
            ListTargetResolution.Missing -> return ListTargetResolution.Missing
        }
        val modelName = modelName(module, target[1])
        val models = module.models
            .filter { it.name.value == modelName }
            .toList()
        val model = when (models.size) {
            0 -> return ListTargetResolution.Missing
            1 -> models.single()
            else -> return ambiguousModelTarget(modelName, models)
        }

        return resolveModelPathTarget(model, target.drop(2))
    }

    private fun resolveModelPathTarget(model: SModel, target: List<String>): ListTargetResolution {
        if (target.isEmpty()) {
            return ListTargetResolution.Found(ListTarget.Model(model))
        }
        val rootSegmentId = parseNodeIdOrNull(target[0])
        val rootNodes = model.rootNodes
            .filter { nodeMatches(it, target[0], rootSegmentId) }
            .toList()
        val rootNode = when (rootNodes.size) {
            0 -> return ListTargetResolution.Missing
            1 -> rootNodes.single()
            else -> return ambiguousRootNodeTarget(target[0], rootNodes)
        }

        if (target.size == 1) {
            return ListTargetResolution.Found(ListTarget.RootNode(rootNode))
        }

        var node = rootNode
        for (segment in target.drop(1)) {
            val segmentId = parseNodeIdOrNull(segment)
            val childNodes = node.children
                .filter { nodeMatches(it, segment, segmentId) }
                .toList()
            node = when (childNodes.size) {
                0 -> return ListTargetResolution.Missing
                1 -> childNodes.single()
                else -> return ambiguousChildNodeTarget(segment, childNodes)
            }
        }
        return ListTargetResolution.Found(ListTarget.Node(node))
    }

    private fun ambiguousModuleTarget(target: String, modules: List<SModule>): ListTargetResolution.Ambiguous =
        ambiguousTarget("module", target, modules) {
            "module\t${it.moduleName}\t${persistence.asString(it.moduleReference)}"
        }

    private fun ambiguousModelTarget(modelName: String, models: List<SModel>): ListTargetResolution.Ambiguous =
        ambiguousTarget("model", modelName, models) {
            "model\t${it.name.value}\t${persistence.asString(it.reference)}"
        }

    private fun ambiguousRootNodeTarget(target: String, nodes: List<SNode>): ListTargetResolution.Ambiguous =
        ambiguousTarget("root node", target, nodes) {
            "root\t${nodeName(it) ?: "<unnamed>"}\t" +
                "${persistence.asString(it.nodeId)}\t${persistence.asString(it.reference)}"
        }

    private fun ambiguousChildNodeTarget(target: String, nodes: List<SNode>): ListTargetResolution.Ambiguous =
        ambiguousTarget("child node", target, nodes) {
            "node\t${it.containmentLink?.role.orEmpty()}\t${nodeName(it) ?: "<unnamed>"}\t" +
                "${persistence.asString(it.nodeId)}\t${persistence.asString(it.reference)}"
        }

    private fun <T> ambiguousTarget(
        kind: String,
        target: String,
        items: List<T>,
        row: (T) -> String,
    ): ListTargetResolution.Ambiguous =
        ListTargetResolution.Ambiguous(
            "ambiguous $kind target $target:\n" + items.joinToString("\n", transform = row),
        )

    private fun resolveNodeReference(target: String): SNode? =
        runCatching {
            persistence.createNodeReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveModelReference(target: String): SModel? =
        runCatching {
            persistence.createModelReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveProjectModuleTarget(target: String): ListTargetResolution {
        val modules = matchingProjectModules(target)
        return when (modules.size) {
            0 -> ListTargetResolution.Missing
            1 -> ListTargetResolution.Found(ListTarget.Module(modules.single()))
            else -> ambiguousModuleTarget(target, modules)
        }
    }

    private fun matchingProjectModules(target: String): List<SModule> =
        project.projectModulesWithGenerators.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }

    private fun modelName(module: SModule, segment: String): String =
        if (segment.startsWith(".") && segment.length > 1) {
            module.moduleName + segment
        } else {
            segment
        }

    private fun nodeName(node: SNode): String? =
        SNodeAccessUtil.getPropertyValue(node, SNodeUtil.property_INamedConcept_name) as String?

    private fun nodeMatches(node: SNode, name: String, nodeId: SNodeId?): Boolean =
        nodeName(node) == name || node.nodeId == nodeId

    private fun parseNodeIdOrNull(nodeId: String): SNodeId? =
        runCatching {
            if (nodeId.all(Char::isDigit)) {
                persistence.createNodeId(nodeId)
            } else {
                IdEncoder().parseNodeId(nodeId)
            }
        }.getOrNull()

    private fun listTargetForNode(node: SNode): ListTarget =
        if (node.parent == null) {
            ListTarget.RootNode(node)
        } else {
            ListTarget.Node(node)
        }

    private inline fun <T> captureRequestErrors(block: () -> T): RequestOutcome<T> =
        try {
            RequestOutcome.Success(block())
        } catch (exception: MpsRequestException) {
            RequestOutcome.Failure(exception)
        }

    private sealed interface RequestOutcome<out T> {
        data class Success<out T>(val value: T) : RequestOutcome<T>
        data class Failure(val exception: MpsRequestException) : RequestOutcome<Nothing>

        fun getOrThrow(): T = when (this) {
            is Success -> value
            is Failure -> throw exception
        }
    }

    private class ScopeResolution(val searchScope: SearchScope, private val containmentRoot: SNode?) {
        // For an explicit subtree scope (a Root Node or nested node) the search runs over the containing model, so the
        // results are narrowed here to those the scope node contains; a repository, module, or model scope has no
        // subtree and keeps every result.
        fun <T> retainWithinSubtree(results: Collection<T>, sourceNode: (T) -> SNode): List<T> {
            val root = containmentRoot ?: return results.toList()
            return results.filter { isAncestorOrSelf(root, sourceNode(it)) }
        }

        private fun isAncestorOrSelf(ancestor: SNode, node: SNode): Boolean {
            var current: SNode? = node
            while (current != null) {
                if (current == ancestor) return true
                current = current.parent
            }
            return false
        }
    }

    private sealed interface ListTarget {
        data class Module(val module: SModule) : ListTarget
        data class Model(val model: SModel) : ListTarget
        data class RootNode(val node: SNode) : ListTarget
        data class Node(val node: SNode) : ListTarget
    }

    private sealed interface ListTargetResolution {
        data class Found(val target: ListTarget) : ListTargetResolution
        data class Ambiguous(val message: String) : ListTargetResolution
        data object Missing : ListTargetResolution
    }
}
