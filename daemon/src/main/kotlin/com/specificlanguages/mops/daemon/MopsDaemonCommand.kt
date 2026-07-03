package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.*
import de.itemis.mps.gradle.project.loader.EnvironmentKind
import de.itemis.mps.gradle.project.loader.ProjectLoader
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

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
        ).runWithMpsAccess(projectDaemon::serve)
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

    fun runWithMpsAccess(action: (MpsAccess) -> Unit) {
        logger.log("verifying environment for project $projectPath")
        val environmentProblem = checkEnvironment()
        if (environmentProblem != null) {
            reportAndThrowStartupError(environmentProblem)
        }

        logger.log("initializing MPS for project $projectPath")

        ProjectLoader
            .build { environmentKind = EnvironmentKind.IDEA }
            .executeWithProject(projectPath.toFile()) { _, project -> action(JetBrainsMpsAccess(project, logger)) }
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
