package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.project.Project
import jetbrains.mps.smodel.JavaFriendlyBase64
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNodeId
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress.getLoopbackAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

/**
 * Daemon process entry command.
 *
 * The command validates the JVM against the requested MPS distribution, opens the MPS project once, writes the daemon
 * record only after the socket is ready, and then serves local protocol requests until stopped or idle.
 */
@Command(
    name = "mops-daemon",
    mixinStandardHelpOptions = true,
    version = ["mops-daemon 0.3.0-SNAPSHOT"],
    description = ["Serve loopback daemon requests until stopped or idle."],
)
class MopsDaemonCommand : Runnable {

    @Option(names = ["--project-path"], required = true)
    lateinit var projectPath: String

    @Option(names = ["--mps-home"], required = true)
    lateinit var mpsHome: String

    @Option(names = ["--workspace-path"], required = true)
    lateinit var workspacePath: String

    @Option(names = ["--token"], required = true)
    lateinit var token: String

    @Option(names = ["--idle-timeout-ms"])
    var idleTimeoutMillis: Long = Duration.ofMinutes(3).toMillis()

    override fun run() {
        val logger = DaemonLogger()
        val projectPath = Path.of(projectPath)
        val mpsHome = Path.of(mpsHome)
        val idleTimeout = Duration.ofMillis(idleTimeoutMillis)

        val workspacePath = Path.of(workspacePath)

        val projectDaemon = ProjectDaemon(
            logger = logger,
            projectPath = projectPath,
            mpsHome = mpsHome,
            token = token,
            idleTimeout = idleTimeout,
            workspace = DaemonWorkspace(workspacePath),
        )

        DaemonRunner(
            projectPath = projectPath,
            mpsHome = mpsHome,
            logger = logger,
        ).runWithProject(projectDaemon::daemonBody)
    }
}

class DaemonLogger() {
    fun log(message: String) {
        println("${Instant.now()} $message")
    }
}

class DaemonRunner(
    val projectPath: Path,
    val mpsHome: Path,
    val logger: DaemonLogger
) {

    fun runWithProject(action: (Project) -> Unit) {
        logger.log("verifying environment for project $projectPath")
        val environmentProblem = checkEnvironment()
        if (environmentProblem != null) {
            reportAndThrowStartupError(environmentProblem)
        }

        logger.log("initializing MPS for project $projectPath")

        ProjectLoader
            .build { environmentKind = EnvironmentKind.IDEA }
            .executeWithProject(projectPath.toFile()) { _, project -> action(project) }
    }

    private fun checkEnvironment(): EnvironmentProblem? =
        checkCurrentJvm(mpsHome)
            ?: environmentCheck(projectPath.isDirectory(), "INVALID_PROJECT_PATH") {
                "project path should be a directory: $projectPath"
            }
            ?: environmentCheck(projectPath.resolve(".mps").isDirectory(), "INVALID_PROJECT_PATH") {
                "project path should contain a .mps directory: $projectPath"
            }
            ?: environmentCheck(mpsHome.isDirectory(), "INVALID_MPS_HOME") {
                "MPS home should be a directory: $mpsHome"
            }
            ?: environmentCheck(mpsHome.resolve("build.properties").isRegularFile(), "INVALID_MPS_HOME") {
                "MPS home should contain a build.properties file: $mpsHome"
            }

    private fun environmentCheck(condition: Boolean, code: String, message: () -> String): EnvironmentProblem? =
        if (!condition) EnvironmentProblem(code, message()) else null

    private fun reportAndThrowStartupError(failure: EnvironmentProblem): Nothing {
        val message = "startup failed: ${failure.message}"
        logger.log(message)
        throw RuntimeException(failure.message)
    }
}

class ProjectDaemon(
    val logger: DaemonLogger,
    val projectPath: Path,
    val workspace: DaemonWorkspace,
    val mpsHome: Path,
    val token: String,
    val idleTimeout: Duration,
) {
    var done = false

    fun daemonBody(project: Project) {
        logger.log("environment ready for project ${projectPath.pathString}")

        ServerSocket(/* port = */ 0, /* backlog = */ 10, /* bindAddr = */ getLoopbackAddress()).use { server ->
            logger.log("ready on ${server.inetAddress.hostAddress}:${server.localPort}")

            workspace.recordWriter().write(
                record = DaemonRecord(
                    port = server.localPort,
                    token = token,
                    pid = ProcessHandle.current().pid(),
                    daemonVersion = "0.3.0-SNAPSHOT",
                    context = DaemonContext.fromLivePaths(
                        projectPath = projectPath,
                        mpsHome = mpsHome,
                        javaHome = Path.of(System.getProperty("java.home"))
                    ),
                    workspace = workspace.path,
                    startupTime = Instant.now().toString(),
                ),
            )

            server.soTimeout = idleTimeout.toMillis().toInt()

            while (!done) {
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    break
                }
                socket.use {
                    connection(socket, project)
                }
            }
        }
    }

    private fun connection(socket: Socket, project: Project) {
        val requestLine = BufferedReader(InputStreamReader(socket.getInputStream())).readLine()

        val response = run {
            val request = try {
                GsonCodec.fromJson(requestLine, DaemonRequest::class.java)
            } catch (exception: RuntimeException) {
                return@run errorResponse(
                    "INVALID_REQUEST",
                    invalidRequestMessage(exception)
                )
            }

            if (request == null) {
                return@run errorResponse(
                    "INVALID_REQUEST",
                    "request must be one newline-delimited JSON object"
                )
            }

            if (request.token != token) {
                return@run errorResponse("TOKEN_MISMATCH", "invalid daemon token: ${request.token}")
            }

            return@run when (request) {
                is PingRequest -> PongResponse(
                    projectPath = projectPath.pathString,
                    mpsHome = mpsHome.pathString,
                    workspacePath = workspace.path.pathString,
                )

                is StopRequest -> StoppedResponse()
                else -> DomainRequestHandler(logger, workspace.path).handleDomainRequest(project, request)
            }
        }

        PrintWriter(socket.getOutputStream(), true).use { writer ->
            writer.println(GsonCodec.toJson(response))
        }
        if (response is StoppedResponse) {
            done = true
        }
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspace.path.pathString)

    private fun invalidRequestMessage(exception: RuntimeException): String =
        exception.message
            ?.takeIf { it == "request type is required" || it.startsWith("unsupported request type ") }
            ?: "request must be one newline-delimited JSON object"

}

class DomainRequestHandler(val logger: DaemonLogger, val workspacePath: Path) {

    fun handleDomainRequest(project: Project, request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> getNode(project, request)
            is ModelResaveRequest -> resaveModel(project, request)
            else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request.type}")
        }
    }

    private fun getNode(project: Project, request: ModelGetNodeRequest): DaemonResponse {
        return try {
            project.modelAccess.computeReadAction {
                val node = resolveNode(project, request)
                    ?: return@computeReadAction errorResponse(
                        code = "NODE_NOT_FOUND",
                        message = "node not found",
                    )
                ModelGetNodeResponse(node = JsonNodeExporter().export(node, includeModel = true))
            }
        } catch (exception: Exception) {
            errorResponse(
                code = "GET_NODE_FAILED",
                message = exception.message ?: exception.javaClass.name,
            )
        }
    }

    private fun resolveNode(project: Project, request: ModelGetNodeRequest): SNode? {
        val nodeReference = request.nodeReference
        if (!nodeReference.isNullOrBlank()) {
            return PersistenceFacade.getInstance()
                .createNodeReference(nodeReference)
                .resolve(project.repository)
        }

        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            throw IllegalArgumentException("modelTarget is required when nodeReference is not provided")
        }
        val nodeId = request.nodeId
        if (nodeId.isNullOrBlank()) {
            throw IllegalArgumentException("nodeId is required when nodeReference is not provided")
        }

        val model = findModel(project, modelTarget)
            ?: throw IllegalArgumentException("model not found: $modelTarget")
        model.load()
        return model.getNode(createNodeId(nodeId))
    }

    private fun createNodeId(nodeId: String): SNodeId {
        val persistence = PersistenceFacade.getInstance()
        val parsed = persistence.createNodeId(nodeId)
        if (parsed != null) {
            return parsed
        }

        val decoded = JavaFriendlyBase64().parseLong(nodeId)
        return requireNotNull(persistence.createNodeId(java.lang.Long.toUnsignedString(decoded))) {
            "could not parse nodeId: $nodeId"
        }
    }

    private fun resaveModel(project: Project, request: ModelResaveRequest): DaemonResponse {
        val modelTarget = request.modelTarget
        if (modelTarget.isNullOrBlank()) {
            return errorResponse("INVALID_REQUEST", "modelTarget is required")
        }

        val future = CompletableFuture<DaemonResponse>()

        project.modelAccess.executeCommandInEDT {
            try {
                val response = project.modelAccess.computeWriteAction {
                    val model = findModel(project, modelTarget)
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
            val cause = if (exception is ExecutionException) exception.cause else exception
            errorResponse(
                code = "SAVE_FAILED",
                message = cause?.message
                    ?: cause?.javaClass?.name
                    ?: exception.message
                    ?: exception.javaClass.name,
            )
        }
    }

    private fun findModel(project: Project, modelTarget: String): SModel? {
        val targetPath = targetPath(modelTarget)
        val candidates = modelCandidates(project).toList()
        val model = candidates
            .firstOrNull { model ->
                model.name.longName == modelTarget ||
                        model.name.value == modelTarget ||
                        PersistenceFacade.getInstance().asString(model.reference) == modelTarget ||
                        targetPath != null && model.filePath() == targetPath
            }
        if (model == null) {
            logger.log(
                "model target $modelTarget not found among ${candidates.size} models: " +
                        candidates.take(20).joinToString { "${it.name.longName} [${it.filePath()}]" })
        }
        return model
    }

    private fun modelCandidates(project: Project): Sequence<SModel> =
        (
                project.projectModulesWithGenerators.asSequence().flatMap { it.models.asSequence() } +
                        project.repository.modules.asSequence().flatMap { it.models.asSequence() }
                ).distinctBy { it.reference }

    private fun targetPath(modelTarget: String): Path? =
        runCatching {
            Path.of(modelTarget).let { path ->
                if (Files.exists(path)) {
                    path.toRealPath()
                } else {
                    path.toAbsolutePath().normalize()
                }
            }
        }.getOrNull()

    private fun SModel.filePath(): Path? {
        val dataSource = source
        if (dataSource is FileDataSource) {
            return runCatching { Path.of(dataSource.file.toRealPath()).toAbsolutePath().normalize() }.getOrNull()
        }
        return runCatching { Path.of(dataSource.location).toAbsolutePath().normalize() }.getOrNull()
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)

}

class JsonNodeExporter(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun export(node: SNode, includeModel: Boolean = false): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val model = node.model
        if (includeModel) {
            model?.let { result["model"] = persistence.asString(it.reference) }
        }
        node.containmentLink?.let { result["role"] = it.role }
        result["concept"] = node.concept.qualifiedName
        result["id"] = persistence.asString(node.nodeId)

        val properties = node.properties
            .mapNotNull { property ->
                node.getProperty(property)?.let { value -> property.name to value }
            }
            .sortedBy { it.first }
            .toMap(LinkedHashMap())
        if (properties.isNotEmpty()) {
            result["properties"] = properties
        }

        val references = node.references
            .map { reference ->
                val target = linkedMapOf<String, Any?>()
                val targetModel = reference.targetSModelReference
                if (targetModel != null && targetModel != model?.reference) {
                    target["model"] = persistence.asString(targetModel)
                }
                reference.targetNodeId?.let { target["node"] = persistence.asString(it) }
                linkedMapOf(
                    "role" to reference.link.role,
                    "target" to target,
                )
            }
            .sortedBy { it["role"] as String }
        if (references.isNotEmpty()) {
            result["references"] = references
        }

        val children = node.children
            .map { export(it, includeModel = false) }
            .toList()
        if (children.isNotEmpty()) {
            result["children"] = children
        }

        return result
    }
}
