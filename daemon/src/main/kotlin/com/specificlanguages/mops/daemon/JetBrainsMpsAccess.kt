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
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.*
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SearchScope
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class JetBrainsMpsAccess(
    private val project: Project,
    logger: DaemonLogger,
    private val mpsListExporter: MpsListExporter = MpsListExporter(),
    private val jsonNodeExporter: JsonNodeExporter = JsonNodeExporter(),
    private val editorNodeRenderer: EditorNodeRenderer = EditorNodeRenderer(),
    private val modelNodeResolver: ModelNodeResolver = ModelNodeResolver(logger),
    private val modelChecker: ModelChecker = ModelChecker(),
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

    // Extra operations run with no model action active: make takes the make framework's own model locks, and render
    // bridges to and blocks on the EDT — either would deadlock inside a read or write action. Enforce that here, once,
    // so the individual operations need not each re-check it. Unlike read/write this deliberately does not wrap the
    // block in computeReadAction; each operation takes its own short model actions as it needs them.
    override fun <T> extra(block: MpsExtra.() -> T): T {
        require(!project.modelAccess.canRead()) {
            "extra operations must run with no read/write action active, or they may deadlock"
        }
        return captureRequestErrors { JetBrainsMpsExtra().block() }.getOrThrow()
    }

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

        override fun checkModel(target: String, limit: Int): ModelCheckResponse {
            val model = modelNodeResolver.findModelUnique(project, target)
                ?: throw MpsRequestException(
                    code = MpsErrorCode.MODEL_NOT_FOUND,
                    message = "model not found: $target",
                )
            return modelChecker.check(project, model, limit)
        }

        // Resolves the node to render and, unless [allowReflective], refuses a subtree whose concepts did not resolve.
        // The editor render itself is deliberately not done here: it must run on the EDT and would deadlock if driven
        // from inside this read action (see [MpsAccess.renderNode]). So this returns the node for the caller to render
        // once the read action has released.
        fun resolveForRender(target: NodeTarget, allowReflective: Boolean): SNode {
            val node = resolveNode(target)
            if (!allowReflective) {
                val unresolvedLanguages = ConceptValidityGuard.unresolvedLanguages(node)
                if (unresolvedLanguages.isNotEmpty()) {
                    throw MpsRequestException(
                        code = MpsErrorCode.LANGUAGE_NOT_LOADED,
                        message = unrenderableSubtreeMessage(unresolvedLanguages),
                    )
                }
            }
            return node
        }

        // One or more concepts in the subtree did not resolve because their language is not loaded. Diagnose each
        // through the shared ModuleLoadDiagnostics so the cause (not built, absent, broken dependency, ...) is reported
        // the same way `find instances` reports an unloaded language, and point at making it or rendering reflectively.
        private fun unrenderableSubtreeMessage(languages: List<String>): String {
            val diagnostics = ModuleLoadDiagnostics(project)
            val causes = languages.joinToString("\n") { language ->
                val diagnostic = diagnostics.diagnoseModule(language).module
                when {
                    diagnostic.problem != null -> moduleLoadRootCauseLines(diagnostic.problem!!)
                    !diagnostic.present -> "  - $language: not a module known to this project"
                    else -> "  - $language: a concept used here is not defined in it"
                }
            }
            return "cannot render: this subtree uses languages that are not loaded, so their concepts did not resolve:\n" +
                "$causes\n" +
                "Make the affected languages (e.g. `mops module make ${languages.first()}`; run " +
                "`mops diagnose module <language>` for the full cause), or pass --allow-reflective to render anyway."
        }

        override fun findUsages(target: NodeTarget, scope: ResolvedScope, limit: Int): FindUsagesResponse {
            val node = resolveNode(target)
            val references = when (val domain = searchDomainFor(scope)) {
                is SearchDomain.MpsScope -> {
                    val collected = CollectConsumer<SReference>()
                    FindUsagesFacade.getInstance()
                        .findUsages(domain.searchScope, setOf(node), collected, EmptyProgressMonitor())
                    collected.result.toList()
                }
                // Walking one subtree touches far fewer nodes than searching its whole model and discarding the rest.
                is SearchDomain.Subtree -> {
                    val targetReference = persistence.asString(node.reference)
                    subtreeNodes(domain.root)
                        .flatMap { it.references.asSequence() }
                        .filter { it.targetNodeReference?.let(persistence::asString) == targetReference }
                        .toList()
                }
            }
            val selected = if (limit > 0) references.take(limit) else references

            return FindUsagesResponse(
                limit = limit,
                truncated = selected.size < references.size,
                usages = selected.map {
                    MpsNodeUsageJson(role = it.link.name, owner = nodeSummary(it.sourceNode, persistence))
                },
            )
        }

        override fun findInstances(
            concept: String,
            exact: Boolean,
            scope: ResolvedScope,
            filters: List<NodeFilter>,
            limit: Int,
        ): FindInstancesResponse {
            val mpsConcept = ConceptResolver(project).resolve(concept)
            val matches = when (val domain = searchDomainFor(scope)) {
                is SearchDomain.MpsScope -> {
                    val collected = CollectConsumer<SNode>()
                    FindUsagesFacade.getInstance()
                        .findInstances(domain.searchScope, setOf(mpsConcept), exact, collected, EmptyProgressMonitor())
                    collected.result.toList()
                }
                // Walking one subtree touches far fewer nodes than searching its whole model and discarding the rest.
                is SearchDomain.Subtree ->
                    subtreeNodes(domain.root).filter { isInstanceOf(it, mpsConcept, exact) }.toList()
            }

            val predicates = filters.map(::compileFilter)
            val instances = matches.filter { node -> predicates.all { it(node) } }
            val selected = if (limit > 0) instances.take(limit) else instances

            return FindInstancesResponse(
                limit = limit,
                truncated = selected.size < instances.size,
                nodes = selected.map { nodeSummary(it, persistence) },
            )
        }

        override fun findByName(pattern: String, scope: ResolvedScope, limit: Int): FindByNameResponse {
            val matcher = nameMatcher(pattern)

            val searchScope = rootBearingScope(scope)
            val matches = mutableListOf<Pair<SNode, Int>>()
            for (model in searchScope.models) {
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
                nodes = selected.map { nodeSummary(it, persistence) },
            )
        }

        override fun diagnoseModules(): ModulesDiagnosticsResponse =
            ModuleLoadDiagnostics(project).diagnoseModules()

        override fun diagnoseModule(module: String): ModuleDiagnosticResponse =
            ModuleLoadDiagnostics(project).diagnoseModule(module)

        // `find root-by-name` searches Root Nodes only, so its scope must bear roots: the default editable project
        // sources, the whole repository, a module, or a model. A subtree scope (a Root Node or nested node) has no
        // roots of its own, so it is rejected with a pointer to the named-descendant search instead.
        private fun rootBearingScope(scope: ResolvedScope): SearchScope =
            when (scope) {
                ResolvedScope.EditableProjectSources -> EditableFilteringScope(project.scope)
                ResolvedScope.Repository -> GlobalScope(project.repository)
                is ResolvedScope.Module -> ModulesScope(listOf(resolveModule(scope.moduleReference)))
                is ResolvedScope.Model -> ModelsScope(listOf(resolveModel(scope.modelReference)))
                is ResolvedScope.Subtree -> throw MpsRequestException(
                    code = MpsErrorCode.UNSUPPORTED_TARGET,
                    message = "find root-by-name searches Root Nodes only, and a node or root-node scope holds none — " +
                        "use `mops find instances <concept> --named <pattern>` to search named descendants of a node.\n" +
                        "see: mops explain scope",
                )
            }

        override fun resolveScope(segments: List<String>?): ResolvedScope =
            when (segments) {
                null, emptyList<String>() -> ResolvedScope.EditableProjectSources
                listOf("/") -> ResolvedScope.Repository
                else -> when (val resolution = resolveListTarget(segments)) {
                    is ListTargetResolution.Found -> resolvedScopeForTarget(resolution.target)
                    is ListTargetResolution.Ambiguous -> throw MpsRequestException(
                        code = MpsErrorCode.AMBIGUOUS_TARGET,
                        message = "${resolution.message}\nsee: mops explain scope",
                    )
                    ListTargetResolution.Missing -> throw MpsRequestException(
                        code = MpsErrorCode.TARGET_NOT_FOUND,
                        message = "scope not found: ${segments.joinToString(" ")} — see: mops explain scope",
                    )
                }
            }

        private fun resolvedScopeForTarget(target: ListTarget): ResolvedScope =
            when (target) {
                is ListTarget.Module -> ResolvedScope.Module(persistence.asString(target.module.moduleReference))
                is ListTarget.Model -> ResolvedScope.Model(persistence.asString(target.model.reference))
                is ListTarget.RootNode -> ResolvedScope.Subtree(persistence.asString(target.node.reference))
                is ListTarget.Node -> ResolvedScope.Subtree(persistence.asString(target.node.reference))
            }

        // Maps a resolved scope to the domain a find runs over. A repository, module, or model scope becomes an MPS
        // Search Scope searched with the platform's index-backed FindUsagesFacade; a subtree scope becomes the scope
        // node itself, whose subtree the find walks node-by-node instead. Each reference re-resolves deterministically
        // because resolution already ruled out ambiguity.
        private fun searchDomainFor(scope: ResolvedScope): SearchDomain =
            when (scope) {
                ResolvedScope.EditableProjectSources -> SearchDomain.MpsScope(EditableFilteringScope(project.scope))
                ResolvedScope.Repository -> SearchDomain.MpsScope(GlobalScope(project.repository))
                is ResolvedScope.Module -> SearchDomain.MpsScope(ModulesScope(listOf(resolveModule(scope.moduleReference))))
                is ResolvedScope.Model -> SearchDomain.MpsScope(ModelsScope(listOf(resolveModel(scope.modelReference))))
                is ResolvedScope.Subtree -> SearchDomain.Subtree(resolveSubtreeNode(scope.nodeReference))
            }

        private fun resolveModule(reference: String): SModule =
            resolveModuleReference(reference)
                ?: throw MpsRequestException(MpsErrorCode.TARGET_NOT_FOUND, "module not found: $reference")

        private fun resolveModel(reference: String): SModel =
            resolveModelReference(reference)
                ?: throw MpsRequestException(MpsErrorCode.MODEL_NOT_FOUND, "model not found: $reference")

        private fun resolveSubtreeNode(reference: String): SNode =
            resolveNodeReference(reference)
                ?: throw MpsRequestException(MpsErrorCode.NODE_NOT_FOUND, "node not found: $reference")

        private fun resolveNode(target: NodeTarget): SNode =
            modelNodeResolver.findNode(project, target)
                ?: throw MpsRequestException(code = MpsErrorCode.NODE_NOT_FOUND, message = "node not found")
    }

    private inner class JetBrainsMpsWrite(
        private val writeScope: WriteTransaction.WriteScope,
    ) : JetBrainsMpsRead(), MpsWrite {
        override fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement): ModelEditResponse =
            editBatchExecutor.apply(project, batch, writeScope, constraints)
    }

    private inner class JetBrainsMpsExtra : MpsExtra {
        private val projectMake = ProjectMake(project)

        override fun makeModules(modules: List<String>): MakeResponse = projectMake.makeModules(modules)

        override fun makeProject(): MakeResponse = projectMake.makeProject()

        // Resolution and rendering run together in one read action on the EDT, required because the editor's disposal
        // asserts the EDT.
        override fun renderNode(target: NodeTarget, allowReflective: Boolean): String {
            val rendered = CompletableFuture<String>()
            project.modelAccess.runReadInEDT {
                try {
                    val node = JetBrainsMpsRead().resolveForRender(target, allowReflective)
                    rendered.complete(editorNodeRenderer.render(node, project))
                } catch (throwable: Throwable) {
                    rendered.completeExceptionally(throwable)
                }
            }
            return try {
                rendered.get()
            } catch (exception: ExecutionException) {
                throw exception.cause ?: exception
            }
        }
    }

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

    private fun resolveModuleReference(target: String): SModule? =
        runCatching {
            persistence.createModuleReference(target).resolve(project.repository)
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

    // Builds the Go-to-Node name matcher shared by `find root-by-name` and the `find instances --named` filter, so the
    // two searches match names by one rule. The leading '*' reproduces MPS Go-to-Node's "search in any place": the
    // pattern matches anywhere within a name, not only as a prefix. The MinusculeMatcher then supports camel-hump and
    // '*' wildcards, and matchingDegree ranks prefix and contiguous matches above scattered middle matches.
    private fun nameMatcher(pattern: String) =
        NameUtil.buildMatcher("*$pattern")
            .withCaseSensitivity(NameUtil.MatchingCaseSensitivity.NONE)
            .build()

    // Compiles one requested filter into a predicate over a found node. A Named filter reuses the Go-to-Node name
    // matcher `find root-by-name` uses, so an unnamed node never matches; a Role filter tests the node's containment
    // role, which a Root Node lacks and so never matches.
    private fun compileFilter(filter: NodeFilter): (SNode) -> Boolean =
        when (filter) {
            is NodeFilter.Named -> {
                val matcher = nameMatcher(filter.pattern)
                val predicate: (SNode) -> Boolean = { node -> nodeName(node)?.let(matcher::matches) == true }
                predicate
            }
            is NodeFilter.Role -> { node: SNode -> node.containmentLink?.role == filter.role }
        }

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

    // The domain a find runs over. An [MpsScope] is searched with the platform's index-backed FindUsagesFacade; a
    // [Subtree] is walked node-by-node, which is cheaper than searching the node's whole model and discarding the rest.
    private sealed interface SearchDomain {
        data class MpsScope(val searchScope: SearchScope) : SearchDomain
        data class Subtree(val root: SNode) : SearchDomain
    }

    // The scope node and every node beneath it, depth-first. `find instances`/`find usages` scoped to a subtree walk
    // this instead of touching the containing model.
    private fun subtreeNodes(root: SNode): Sequence<SNode> =
        sequence {
            yield(root)
            for (child in root.children) yieldAll(subtreeNodes(child))
        }

    // Whether [node] is an instance of [concept], matching FindUsagesFacade's `exact` flag: exact requires the node's
    // direct concept; otherwise a subconcept also matches (isSubConceptOf is reflexive, so it covers the concept itself).
    private fun isInstanceOf(node: SNode, concept: SAbstractConcept, exact: Boolean): Boolean =
        if (exact) node.concept == concept else node.concept.isSubConceptOf(concept)

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
