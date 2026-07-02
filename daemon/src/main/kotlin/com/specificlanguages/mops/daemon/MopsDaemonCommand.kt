package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import jetbrains.mps.project.Project
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress.getLoopbackAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
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
                    connection(socket, JetBrainsMpsAccess(project = project, logger = logger))
                }
            }
        }
    }

    private fun connection(socket: Socket, mpsAccess: MpsAccess) {
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
                else -> DomainRequestHandler(workspace.path, mpsAccess).handleDomainRequest(request)
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
