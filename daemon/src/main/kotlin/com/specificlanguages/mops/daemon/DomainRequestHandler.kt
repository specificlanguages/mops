package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.EditableFilteringScope
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.persistence.def.v9.IdEncoder
import jetbrains.mps.util.CollectConsumer
import org.jetbrains.mps.openapi.model.*
import org.jetbrains.mps.openapi.module.FindUsagesFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.pathString

class DomainRequestHandler(val logger: DaemonLogger, val workspacePath: Path) {
    private val modelNodeResolver = ModelNodeResolver(logger)
    private val mpsListExporter = MpsListExporter()
    private val persistence = PersistenceFacade.getInstance()

    fun handleDomainRequest(project: Project, request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> getNode(project, request)
            is FindUsagesRequest -> findUsages(project, request)
            is FindInstancesRequest -> findInstances(project, request)
            is ModelResaveRequest -> resaveModel(project, request)
            is MpsListRequest -> list(project, request)
            else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request.type}")
        }
    }

    private fun list(project: Project, request: MpsListRequest): DaemonResponse {
        return project.modelAccess.computeReadAction {
            val target = request.target
            val root = when (target) {
                null -> mpsListExporter.exportProject(project, request.depth)
                listOf("/") -> mpsListExporter.exportRepository(project.repository, request.depth)
                else -> {
                    val root = when (val resolution = resolveListTarget(project, target)) {
                        is ListTargetResolution.Found -> resolution.target
                        is ListTargetResolution.Ambiguous -> return@computeReadAction errorResponse(
                            code = "AMBIGUOUS_TARGET",
                            message = resolution.message,
                        )
                        ListTargetResolution.Missing -> return@computeReadAction errorResponse(
                            code = "TARGET_NOT_FOUND",
                            message = "target not found: ${target.joinToString(" ")}",
                        )
                    }
                    when (root) {
                        is ListTarget.Module -> mpsListExporter.exportModule(root.module, request.depth)
                        is ListTarget.Model -> mpsListExporter.exportModel(root.model, request.depth)
                        is ListTarget.RootNode -> mpsListExporter.exportRoot(root.node, request.depth)
                        is ListTarget.Node -> mpsListExporter.exportNode(root.node, request.depth)
                    }
                }
            }
            MpsListResponse(root = root)
        }
    }

    private fun resolveListTarget(project: Project, target: List<String>): ListTargetResolution {
        if (target.isEmpty()) {
            return ListTargetResolution.Missing
        }

        if (target.size == 1) {
            resolveNodeReference(project, target.single())?.let {
                return ListTargetResolution.Found(listTargetForNode(it))
            }
        }

        resolveModelReference(project, target[0])?.let {
            return resolveModelPathTarget(it, target.drop(1))
        }

        if (target.size == 1) {
            return resolveProjectModuleTarget(project, target.single())
        }

        val module = when (val resolution = resolveProjectModuleTarget(project, target[0])) {
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

    private fun resolveNodeReference(project: Project, target: String): SNode? =
        runCatching {
            persistence.createNodeReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveModelReference(project: Project, target: String): SModel? =
        runCatching {
            persistence.createModelReference(target).resolve(project.repository)
        }.getOrNull()

    private fun resolveProjectModuleTarget(project: Project, target: String): ListTargetResolution {
        val modules = matchingProjectModules(project, target)
        return when (modules.size) {
            0 -> ListTargetResolution.Missing
            1 -> ListTargetResolution.Found(ListTarget.Module(modules.single()))
            else -> ambiguousModuleTarget(target, modules)
        }
    }

    private fun matchingProjectModules(project: Project, target: String): List<SModule> =
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

    private fun getNode(project: Project, request: ModelGetNodeRequest): DaemonResponse =
        withResolvedNode(project, request.target, failureCode = "GET_NODE_FAILED") { node ->
            ModelGetNodeResponse(node = JsonNodeExporter().export(node))
        }

    private fun findUsages(project: Project, request: FindUsagesRequest): DaemonResponse =
        withResolvedNode(project, request.target, failureCode = "FIND_USAGES_FAILED") { target ->
            val scope = EditableFilteringScope(project.scope)

            val collected = CollectConsumer<SReference>()
            FindUsagesFacade.getInstance().findUsages(scope, setOf(target), collected, EmptyProgressMonitor())

            val references = collected.result
            val selected = if (request.limit > 0) references.take(request.limit) else references

            FindUsagesResponse(
                limit = request.limit,
                truncated = selected.size < references.size,
                usages = selected.map { MpsNodeUsageJson(role = it.link.name, owner = nodeSummary(it.sourceNode)) },
            )
        }

    private fun findInstances(project: Project, request: FindInstancesRequest): DaemonResponse =
        try {
            project.modelAccess.computeReadAction {
                val concept = ConceptRegistry.getInstance().getConceptByName(request.concept)
                if (!concept.isValid) {
                    return@computeReadAction errorResponse(
                        code = "CONCEPT_NOT_FOUND",
                        message = "concept not found: ${request.concept}",
                    )
                }

                val scope = EditableFilteringScope(project.scope)
                val collected = CollectConsumer<SNode>()
                FindUsagesFacade.getInstance()
                    .findInstances(scope, setOf(concept), request.exact, collected, EmptyProgressMonitor())

                val instances = collected.result
                val selected = if (request.limit > 0) instances.take(request.limit) else instances

                FindInstancesResponse(
                    limit = request.limit,
                    truncated = selected.size < instances.size,
                    nodes = selected.map { nodeSummary(it) },
                )
            }
        } catch (exception: Exception) {
            errorResponse(
                code = "FIND_INSTANCES_FAILED",
                message = exception.message ?: exception.javaClass.name,
            )
        }

    /**
     * Resolves [target] inside a read action and passes the node to [body], translating a resolve
     * miss into NODE_NOT_FOUND and any thrown exception into an error response with [failureCode].
     */
    private fun withResolvedNode(
        project: Project,
        target: NodeTarget,
        failureCode: String,
        body: (SNode) -> DaemonResponse,
    ): DaemonResponse =
        try {
            project.modelAccess.computeReadAction {
                val node = modelNodeResolver.findNode(project, target)
                    ?: return@computeReadAction errorResponse(
                        code = "NODE_NOT_FOUND",
                        message = "node not found",
                    )
                body(node)
            }
        } catch (exception: Exception) {
            errorResponse(
                code = failureCode,
                message = exception.message ?: exception.javaClass.name,
            )
        }

    private fun nodeSummary(node: SNode): MpsNodeSummaryJson =
        MpsNodeSummaryJson(
            type = if (node.parent == null) "root" else "node",
            name = nodeName(node),
            concept = node.concept.qualifiedName,
            reference = persistence.asString(node.reference),
        )

    private fun resaveModel(project: Project, request: ModelResaveRequest): DaemonResponse {
        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            return errorResponse("INVALID_REQUEST", "modelTarget is required")
        }

        val future = CompletableFuture<DaemonResponse>()

        project.modelAccess.executeCommandInEDT {
            try {
                val response = project.modelAccess.computeWriteAction {
                    val model = modelNodeResolver.findModel(project, modelTarget)
                        ?: return@computeWriteAction errorResponse(
                            code = "MODEL_NOT_FOUND",
                            message = "model not found: $modelTarget",
                        )
                    if (model.isReadOnly || model !is EditableSModel) {
                        return@computeWriteAction errorResponse(
                            code = "MODEL_READ_ONLY",
                            message = "model is not editable: ${model.name.longName}",
                        )
                    }

                    model.load()

                    val result = model.save(SaveOptions.FORCE_SAVE_WITH_RESOLVE_INFO).toCompletableFuture().join()

                    if (result != SaveResult.SAVED_TO_DATA_SOURCE && result != SaveResult.NOT_CHANGED) {
                        return@computeWriteAction errorResponse(
                            code = "SAVE_FAILED",
                            message = "model save failed for ${model.name.longName}: $result",
                        )
                    }

                    ModelResaveResponse(modelTarget = modelTarget)
                }
                future.complete(response)
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }

        return try {
            future.get()
        } catch (exception: Exception) {
            val cause = if (exception is ExecutionException) exception.cause ?: exception else exception
            errorResponse(
                code = "SAVE_FAILED",
                message = cause.message ?: cause.javaClass.name,
            )
        }
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
