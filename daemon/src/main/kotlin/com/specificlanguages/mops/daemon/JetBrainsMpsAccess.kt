package com.specificlanguages.mops.daemon

import com.intellij.psi.codeStyle.NameUtil
import com.specificlanguages.mops.daemon.core.*
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.ide.findusages.model.scopes.ModelsScope
import jetbrains.mps.ide.findusages.model.scopes.ModulesScope
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.EditableFilteringScope
import jetbrains.mps.project.GlobalScope
import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.*
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SearchScope
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

class JetBrainsMpsAccess(
    internal val project: MPSProject,
    logger: DaemonLogger,
    private val workspace: Path,
    private val testSystemCache: Path,
    private val platform: Platform,
    private val mpsListExporter: MpsListExporter = MpsListExporter(),
    private val jsonNodeExporter: JsonNodeExporter = JsonNodeExporter(),
    private val moduleLocationsExporter: ModuleLocationsExporter = ModuleLocationsExporter(),
    private val editorNodeRenderer: EditorNodeRenderer = EditorNodeRenderer(),
    private val modelNodeResolver: ModelNodeResolver = ModelNodeResolver(logger),
    private val modelChecker: ModelChecker = ModelChecker(),
    private val editBatchExecutor: EditBatchExecutor = EditBatchExecutor(modelNodeResolver),
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
    private val writeTransaction: WriteTransaction = WriteTransaction(),
) : MpsAccess {
    private val externalProjectRefresh = ExternalProjectRefresh(project, logger)
    private val navigationTargets = NavigationTargetResolver(project, persistence)

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
        override fun list(target: List<String>?, depth: Int, limit: Int, summary: Boolean, role: String?): MpsListEntryJson =
            when (target) {
                null -> {
                    rejectRoleOnNonNode(role, "project")
                    mpsListExporter.exportProject(project, depth, limit, summary)
                }
                listOf("/") -> {
                    rejectRoleOnNonNode(role, "repository")
                    mpsListExporter.exportRepository(project.repository, depth, limit, summary)
                }
                else -> {
                    val root = when (val resolution = navigationTargets.resolve(target)) {
                        is NavigationTargetResolution.Found -> resolution.target
                        is NavigationTargetResolution.Ambiguous -> throw MpsRequestException(
                            code = MpsErrorCode.AMBIGUOUS_TARGET,
                            message = resolution.message,
                        )
                        is NavigationTargetResolution.Missing -> throw MpsRequestException(
                            code = MpsErrorCode.TARGET_NOT_FOUND,
                            message = "target not found: ${target.joinToString(" ")}" + resolution.detailLine(),
                        )
                    }
                    when (root) {
                        is NavigationTarget.Module -> {
                            rejectRoleOnNonNode(role, "module")
                            mpsListExporter.exportModule(root.module, depth, limit, summary)
                        }
                        is NavigationTarget.Model -> {
                            rejectRoleOnNonNode(role, "model")
                            mpsListExporter.exportModel(root.model, depth, limit, summary)
                        }
                        is NavigationTarget.RootNode -> mpsListExporter.exportRoot(root.node, depth, limit, summary, role)
                        is NavigationTarget.Node -> mpsListExporter.exportNode(root.node, depth, limit, summary, role)
                    }
                }
            }

        // --role restricts a node's children to one Containment Role, so it is meaningful only for a node target. A
        // module, model, project, or repository target has no containment roles; reject --role there instead of
        // silently ignoring it.
        private fun rejectRoleOnNonNode(role: String?, targetKind: String) {
            if (role != null) {
                throw MpsRequestException(
                    code = MpsErrorCode.UNSUPPORTED_TARGET,
                    message = "--role is valid only for a node target, but this target is a $targetKind, which has no " +
                        "containment roles. List a node (root or nested) to filter its children by role.",
                )
            }
        }

        override fun getNode(target: NodeTarget, ancestry: Boolean, depth: Int?): MpsNodeJson =
            jsonNodeExporter.export(resolveNode(target), ancestry, depth)

        override fun getNodeSummary(target: NodeTarget, ancestry: Boolean): MpsNodeSummaryJson =
            jsonNodeExporter.exportSummary(resolveNode(target), ancestry)

        override fun getModule(target: String): ModuleLocationsJson {
            val matches = project.repository.modulesMatching(target)
            val module = when (matches.size) {
                0 -> throw MpsRequestException(MpsErrorCode.MODULE_NOT_FOUND, "module not found: $target")
                1 -> matches.single()
                else -> throw MpsRequestException(
                    MpsErrorCode.AMBIGUOUS_TARGET,
                    "ambiguous module: $target; candidates: ${matches.joinToString { persistence.asString(it.moduleReference) }}",
                )
            }
            return moduleLocationsExporter.export(module)
        }

        override fun checkModel(target: String, limit: Int): ModelCheckResponse {
            val model = modelNodeResolver.findModelUnique(project, target)
                ?: throw MpsRequestException(
                    code = MpsErrorCode.MODEL_NOT_FOUND,
                    message = "model not found: $target",
                )
            return modelChecker.check(project, model, limit)
        }

        override fun checkProject(limit: Int): ModelCheckResponse =
            modelChecker.check(project, project.projectModulesWithGenerators.toList(), limit)

        override fun checkModules(modules: List<String>, limit: Int): ModelCheckResponse =
            modelChecker.check(project, modules.map(::resolveCheckModule).distinct(), limit)

        private fun resolveCheckModule(target: String): SModule {
            val matches = navigationTargets.projectModulesMatching(target)
            return when (matches.size) {
                0 -> throw MpsRequestException(MpsErrorCode.TARGET_NOT_FOUND, "module not found in project: $target")
                1 -> matches.single()
                else -> throw MpsRequestException(
                    MpsErrorCode.AMBIGUOUS_TARGET,
                    "ambiguous module: $target matches ${matches.size} project modules",
                )
            }
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
            val diagnosed = languages.map { it to diagnostics.diagnoseModule(it).module }
            val causes = diagnosed.joinToString("\n") { (language, diagnostic) ->
                when {
                    diagnostic.problem != null -> moduleLoadRootCauseLines(diagnostic.problem!!)
                    !diagnostic.present -> "  - $language: not a module known to this project"
                    else -> "  - $language: a concept used here is not defined in it"
                }
            }
            // The languages a make would fix (unbuilt, alone or through their dependency closure); an absent or
            // otherwise unbuildable one is left to the diagnosis and --allow-reflective rather than a misleading make.
            val buildable = diagnosed.mapNotNull { (language, diagnostic) ->
                language.takeIf { diagnostic.problem?.let(::loadFixableByMake) == true }
            }
            val remedy = if (buildable.isEmpty()) {
                "Run `mops diagnose module <language>` for the full cause, or pass --allow-reflective to render anyway."
            } else {
                "Make the affected languages (`mops make module ${buildable.joinToString(" ")}`), then retry; run " +
                    "`mops diagnose module <language>` for the full cause, or pass --allow-reflective to render anyway."
            }
            return "cannot render: this subtree uses languages that are not loaded, so their concepts did not resolve:\n" +
                "$causes\n" +
                remedy
        }

        override fun findUsages(target: NodeTarget, scope: ResolvedScope, limit: Int): FindUsagesResponse {
            val node = resolveNode(target)
            val domain = searchDomainFor(scope)
            val references = when (domain) {
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
                target = nodeSummary(node, persistence),
                scope = searchedScope(domain),
                limit = limit,
                truncated = selected.size < references.size,
                usages = selected.map {
                    MpsNodeUsageJson(role = it.link.name, owner = nodeSummary(it.sourceNode, persistence))
                },
            )
        }

        override fun findSubconcepts(concept: String, concreteOnly: Boolean, direct: Boolean): FindSubconceptsResponse {
            val mpsConcept = ConceptResolver(project).resolve(concept)
            return FindSubconceptsResponse(
                target = ConceptTargetJson(mpsConcept.qualifiedName),
                scope = SearchScopeJson(SearchScopeKind.LOADED_LANGUAGES),
                concepts = ConceptHierarchy.subConceptsOf(mpsConcept, concreteOnly, direct).map { it.qualifiedName },
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
            val domain = searchDomainFor(scope)
            val matches = when (domain) {
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
                target = ConceptTargetJson(mpsConcept.qualifiedName),
                scope = searchedScope(domain),
                limit = limit,
                truncated = selected.size < instances.size,
                nodes = selected.map { nodeSummary(it, persistence) },
            )
        }

        override fun findByName(pattern: String, scope: ResolvedScope, limit: Int): FindByNameResponse {
            val matcher = nameMatcher(pattern)

            val domain = rootBearingDomain(scope)
            val matches = mutableListOf<Pair<SNode, Int>>()
            for (model in domain.searchScope.models) {
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
                target = NamePatternTargetJson(pattern),
                scope = searchedScope(domain),
                limit = limit,
                truncated = selected.size < ordered.size,
                nodes = selected.map { nodeSummary(it, persistence) },
            )
        }

        override fun findNodeById(nodeId: String, scope: ResolvedScope, limit: Int): FindNodeByIdResponse {
            val id = parseNodeIdOrNull(persistence, nodeId)
                ?: throw MpsRequestException(
                    code = MpsErrorCode.INVALID_REQUEST,
                    message = "could not parse node ID: $nodeId",
                )

            // A Node ID is unique within a model, so at most one node per model matches; collecting across the scope's
            // models yields one row per model that holds it, never a guess between them.
            val domain = searchDomainFor(scope)
            val matches = when (domain) {
                is SearchDomain.MpsScope -> domain.searchScope.models.mapNotNull { it.getNode(id) }
                is SearchDomain.Subtree -> subtreeNodes(domain.root).filter { it.nodeId == id }.toList()
            }

            val ordered = matches.sortedBy { persistence.asString(it.reference) }
            val selected = if (limit > 0) ordered.take(limit) else ordered

            return FindNodeByIdResponse(
                target = NodeIdTargetJson(id.toString()),
                scope = searchedScope(domain),
                limit = limit,
                truncated = selected.size < ordered.size,
                nodes = selected.map { nodeSummary(it, persistence) },
            )
        }

        override fun diagnoseModules(): ModulesDiagnosticsResponse =
            ModuleLoadDiagnostics(project).diagnoseModules()

        override fun diagnoseModule(module: String): ModuleDiagnosticResponse =
            ModuleLoadDiagnostics(project).diagnoseModule(module)

        // `find root-by-name` searches root nodes only, so its scope must bear roots: the default editable project
        // sources, the whole repository, a module, or a model. A subtree scope (a root node or nested node) has no
        // roots of its own, so it is rejected with a pointer to the named-descendant search instead.
        private fun rootBearingDomain(scope: ResolvedScope): SearchDomain.MpsScope =
            when (val domain = searchDomainFor(scope)) {
                is SearchDomain.MpsScope -> domain
                is SearchDomain.Subtree -> throw MpsRequestException(
                    code = MpsErrorCode.UNSUPPORTED_TARGET,
                    message = "find root-by-name searches root nodes only, and a node or root-node scope holds none — " +
                        "use `mops find instances <concept> --named <pattern>` to search named descendants of a node.\n" +
                        "see: mops explain scope",
                )
            }

        override fun resolveScope(segments: List<String>?): ResolvedScope =
            when (segments) {
                null, emptyList<String>() -> ResolvedScope.EditableProjectSources
                listOf("/") -> ResolvedScope.Repository
                else -> when (val resolution = navigationTargets.resolve(segments)) {
                    is NavigationTargetResolution.Found -> resolvedScopeForTarget(resolution.target)
                    is NavigationTargetResolution.Ambiguous -> throw MpsRequestException(
                        code = MpsErrorCode.AMBIGUOUS_TARGET,
                        message = "${resolution.message}\nsee: mops explain scope",
                    )
                    is NavigationTargetResolution.Missing -> throw MpsRequestException(
                        code = MpsErrorCode.TARGET_NOT_FOUND,
                        message = "scope not found: ${segments.joinToString(" ")}" + resolution.detailLine() +
                            "\nsee: mops explain scope",
                    )
                }
            }

        private fun resolvedScopeForTarget(target: NavigationTarget): ResolvedScope =
            when (target) {
                is NavigationTarget.Module -> ResolvedScope.Module(persistence.asString(target.module.moduleReference))
                is NavigationTarget.Model -> ResolvedScope.Model(persistence.asString(target.model.reference))
                is NavigationTarget.RootNode -> ResolvedScope.Subtree(persistence.asString(target.node.reference))
                is NavigationTarget.Node -> ResolvedScope.Subtree(persistence.asString(target.node.reference))
            }

        // Maps a resolved scope to the domain a find runs over. A repository, module, or model scope becomes an MPS
        // search scope searched with the platform's index-backed FindUsagesFacade; a subtree scope becomes the scope
        // node itself, whose subtree the find walks node-by-node instead. Each reference re-resolves deterministically
        // because resolution already ruled out ambiguity.
        private fun searchDomainFor(scope: ResolvedScope): SearchDomain =
            when (scope) {
                ResolvedScope.EditableProjectSources -> SearchDomain.MpsScope(
                    EditableFilteringScope(project.scope),
                    SearchScopeJson(SearchScopeKind.EDITABLE_PROJECT_SOURCES),
                )
                ResolvedScope.Repository -> SearchDomain.MpsScope(
                    GlobalScope(project.repository),
                    SearchScopeJson(SearchScopeKind.REPOSITORY),
                )
                is ResolvedScope.Module -> resolveModule(scope.moduleReference).let {
                    SearchDomain.MpsScope(
                        ModulesScope(listOf(it)),
                        SearchScopeJson(SearchScopeKind.MODULE, scope.moduleReference, it.moduleName),
                    )
                }
                is ResolvedScope.Model -> resolveModel(scope.modelReference).let {
                    SearchDomain.MpsScope(
                        ModelsScope(listOf(it)),
                        SearchScopeJson(SearchScopeKind.MODEL, scope.modelReference, it.name.value),
                    )
                }
                is ResolvedScope.Subtree -> SearchDomain.Subtree(resolveSubtreeNode(scope.nodeReference))
            }

        // Describes the domain a find searched for its response. The model count is that of the MPS search scope the
        // find ran over; a subtree is walked node by node, so it carries no count.
        private fun searchedScope(domain: SearchDomain): SearchScopeJson =
            when (domain) {
                is SearchDomain.MpsScope -> domain.description.copy(models = domain.searchScope.models.count())
                is SearchDomain.Subtree -> SearchScopeJson(
                    kind = SearchScopeKind.SUBTREE,
                    reference = persistence.asString(domain.root.reference),
                    name = nodeName(domain.root),
                )
            }

        private fun resolveModule(reference: String): SModule =
            navigationTargets.resolveModuleReference(reference)
                ?: throw MpsRequestException(MpsErrorCode.TARGET_NOT_FOUND, "module not found: $reference")

        private fun resolveModel(reference: String): SModel =
            navigationTargets.resolveModelReference(reference)
                ?: throw MpsRequestException(MpsErrorCode.MODEL_NOT_FOUND, "model not found: $reference")

        private fun resolveSubtreeNode(reference: String): SNode =
            navigationTargets.resolveNodeReference(reference)
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

        override fun createModule(request: CreateModuleRequest): ModuleCreationResponse =
            ModuleCreationCliAdapter(ModuleCreator(project)).create(request).also {
                if (!request.dryRun) project.repository.saveAll()
            }

        override fun createModel(request: CreateModelRequest): ModelCreationResponse = ModelCreator(project).create(request)
    }

    private inner class JetBrainsMpsExtra : MpsExtra {
        private val projectMake = ProjectMake(project)

        override fun runTests(request: TestRunRequest): TestRunReport =
            projectTesting().run(request.target, request.build, request.timeoutMillis, request.cancellationPath)

        override fun runCode(request: CodeRunRequest): CodeResultResponse =
            CodeModeExecutor(this@JetBrainsMpsAccess, project, platform, projectTesting()).execute(request)

        private fun projectTesting() = ProjectTesting(this@JetBrainsMpsAccess, workspace, testSystemCache)

        override fun refreshExternalChanges() {
            externalProjectRefresh.refresh()
        }

        override fun saveProject() = saveProjectSettings(project)

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
    // role, which a root node lacks and so never matches.
    private fun compileFilter(filter: NodeFilter): (SNode) -> Boolean =
        when (filter) {
            is NodeFilter.Named -> {
                val matcher = nameMatcher(filter.pattern)
                val predicate: (SNode) -> Boolean = { node -> nodeName(node)?.let(matcher::matches) == true }
                predicate
            }
            is NodeFilter.Role -> { node: SNode -> node.containmentLink?.role == filter.role }
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
        // [description] names the scope without its model count, which `searchedScope` adds.
        data class MpsScope(val searchScope: SearchScope, val description: SearchScopeJson) : SearchDomain
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
}
