package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.daemoncomms.DefaultDaemonPool
import com.specificlanguages.mops.launcher.MpsDistributionLayout
import com.specificlanguages.mops.protocol.DaemonContext
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Path
import kotlin.io.path.isDirectory

@Command(
    name = "mops",
    mixinStandardHelpOptions = true,
    version = ["mops 0.3.0-SNAPSHOT"],
    description = ["Kotlin CLI for the daemon-backed MPS prototype."],
    subcommands = [
        DaemonOperations::class,
        MpsListCommand::class,
        ModelOperations::class,
    ],
)
class MopsCommand(
    /**
     * The working directory for the CLI. All paths are resolved relative to this directory.
     */
    val workingDirectory: Path = Path.of(System.getProperty("user.dir"))
) : Runnable {

    init {
        require(workingDirectory.isAbsolute) { "working directory must be absolute: $workingDirectory" }
    }

    @Option(
        names = ["--mps-home"],
        paramLabel = "PATH",
        description = ["MPS home used by daemon-backed commands."],
    )
    var mpsHome: String? = null

    @Option(
        names = ["--daemon-home"],
        paramLabel = "PATH",
        description = ["Daemon home used by daemon-backed commands."],
    )
    var daemonHome: String? = null

    @Option(
        names = ["--java-home"],
        paramLabel = "PATH",
        description = ["Java home used to start daemon-backed commands."],
    )
    var javaHome: String? = null

    override fun run() {
        CommandLine(this).usage(System.out)
    }

    private var daemonPool: DefaultDaemonPool? = null

    fun ensureDaemonPool(): DaemonPool =
        daemonPool ?: createDaemonPool().also { daemonPool = it }

    fun ensureDaemon(projectPathHint: Path = workingDirectory): DaemonClient =
        ensureDaemonPool().ensureDaemon(createDaemonContext(projectPathHint))

    private fun createDaemonPool(): DefaultDaemonPool {
        val resolvedDaemonHome =
            daemonHome?.let(workingDirectory::resolve) ?: Path.of(System.getProperty("user.home"), ".mops", "daemon")
        val daemonPool = DefaultDaemonPool.forDaemonHome(resolvedDaemonHome)
        return daemonPool
    }

    fun createDaemonContext(projectPathHint: Path): DaemonContext {
        val resolvedMpsHome = resolveMpsHome() ?: throw MpsHomeRequiredException()
        val resolvedJavaHome =
            javaHome?.let(workingDirectory::resolve)
                ?: MpsDistributionLayout.findBundledJavaHome(resolvedMpsHome)
                ?: throw IllegalStateException("MPS distribution at $resolvedMpsHome does not bundle Java, specify Java home explicitly")

        val projectPath = resolveProjectPath(projectPathHint)

        return DaemonContext.fromLivePaths(
            projectPath = projectPath,
            mpsHome = resolvedMpsHome,
            javaHome = resolvedJavaHome
        )
    }

    fun resolveMpsHome(): Path? = mpsHome?.let(workingDirectory::resolve)

    fun resolveProjectPath(start: Path = workingDirectory): Path = MopsCommand.resolveProjectPath(start)

    companion object {
        fun resolveProjectPath(start: Path): Path {
            require(start.isAbsolute) { "project path must be absolute: $start" }
            var current: Path? = start.normalize()

            while (current != null) {
                if (current.resolve(".mps").isDirectory()) {
                    return current
                }
                current = current.parent
            }

            throw ProjectPathNotFoundException(start)
        }

    }
}
