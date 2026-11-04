package com.specificlanguages.mops.cli

import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.mpsBuiltInPathMacros
import com.specificlanguages.mops.cli.output.NodeReferenceFormat
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.daemoncomms.DaemonPool
import com.specificlanguages.mops.daemoncomms.DefaultDaemonPool
import com.specificlanguages.mops.launcher.MpsDistributionLayout
import com.specificlanguages.mops.protocol.DaemonContext
import java.nio.file.Path
import java.util.Stack
import kotlin.io.path.isDirectory
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.ArgSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ScopeType
import picocli.CommandLine.Spec

@Command(
    name = "mops",
    mixinStandardHelpOptions = true,
    versionProvider = MopsVersionProvider::class,
    description = ["Kotlin CLI for the daemon-backed MPS prototype."],
    footer = [
        "",
        "Global options can appear before, between, or after command names; command options belong after their " +
            "command name. mops explain output describes how output separates results, metadata, and diagnostics.",
        "",
        "Start with mops examples for task recipes; mops examples editing helps choose an editing approach.",
    ],
)
class MopsCommand(
    /**
     * The working directory for the CLI. All paths are resolved relative to this directory.
     */
    override val workingDirectory: Path = Path.of(System.getProperty("user.dir"))
) : Runnable, CommandEnvironment {

    init {
        require(workingDirectory.isAbsolute) { "working directory must be absolute: $workingDirectory" }
    }

    @Option(
        names = ["--mps-home"],
        scope = ScopeType.INHERIT,
        paramLabel = "PATH",
        description = ["MPS home used by daemon-backed commands."],
    )
    var mpsHome: String? = null

    @Option(
        names = ["--project-root"],
        scope = ScopeType.INHERIT,
        paramLabel = "PATH",
        description = ["Explicit MPS project root, absolute or relative to the directory where mops was started. Overrides upward inference from the working directory."],
    )
    var projectRoot: String? = null

    @Option(
        names = ["--daemon-home"],
        scope = ScopeType.INHERIT,
        paramLabel = "PATH",
        description = ["Daemon home used by daemon-backed commands."],
    )
    var daemonHome: String? = null

    @Option(
        names = ["--java-home"],
        scope = ScopeType.INHERIT,
        paramLabel = "PATH",
        description = ["Java home used to start daemon-backed commands."],
    )
    var javaHome: String? = null

    @Option(
        names = ["--macro"],
        scope = ScopeType.INHERIT,
        paramLabel = "NAME=PATH",
        description = [
            "Path macro the daemon defines in MPS before it opens the project; repeatable. PATH is an existing " +
                "directory, absolute or relative to the directory where mops was started. Test runs pass the macros " +
                "to the test worker. A running daemon with other macros is not reused; stop it with mops daemon stop.",
        ],
        parameterConsumer = MacroConsumer::class,
    )
    private var macroOption: String? = null

    /** `--macro` values by name, as given. */
    private val macros: MutableMap<String, String> = linkedMapOf()

    /**
     * Collects every `--macro` into the root command. A plain repeatable option would reject a repetition on one command
     * level, and a collection field would restart at each subcommand that the option is inherited by.
     */
    private class MacroConsumer : CommandLine.IParameterConsumer {
        override fun consumeParameters(args: Stack<String>, argSpec: ArgSpec, commandSpec: CommandLine.Model.CommandSpec) {
            val commandLine = commandSpec.commandLine()
            if (args.isEmpty()) throw CommandLine.ParameterException(commandLine, "--macro expects NAME=PATH")
            val macro = args.pop()
            val name = macro.substringBefore('=', missingDelimiterValue = "")
            val path = macro.substringAfter('=')
            if (name.isEmpty() || path.isEmpty()) {
                throw CommandLine.ParameterException(commandLine, "--macro expects NAME=PATH, got: $macro")
            }
            (commandSpec.root().userObject() as MopsCommand).macros.put(name, path)?.let {
                throw CommandLine.ParameterException(commandLine, "--macro $name is given twice: $it and $path")
            }
        }
    }

    override var nodeReferenceFormat: NodeReferenceFormat = NodeReferenceFormat.SERIALIZED

    @Option(names = ["--refs-as-urls"], scope = ScopeType.INHERIT, description = ["Render node references as MPS URLs in text output; JSON output is unchanged."])
    fun setRefsAsUrls(enabled: Boolean) {
        nodeReferenceFormat = if (enabled) NodeReferenceFormat.URL else NodeReferenceFormat.SERIALIZED
    }

    @Spec
    lateinit var commandSpec: CommandLine.Model.CommandSpec

    override fun run() {
        commandSpec.commandLine().usage(System.out)
    }

    private var daemonPool: DefaultDaemonPool? = null

    override fun daemonPool(): DaemonPool =
        daemonPool ?: createDaemonPool().also { daemonPool = it }

    override fun daemon(projectPathHint: Path): DaemonClient =
        daemonPool().ensureDaemon(createDaemonContext(projectPathHint))

    private fun createDaemonPool(): DefaultDaemonPool {
        val resolvedDaemonHome =
            daemonHome?.let(workingDirectory::resolve) ?: defaultDaemonHome()
        val daemonPool = DefaultDaemonPool.forDaemonHome(resolvedDaemonHome)
        return daemonPool
    }

    fun createDaemonContext(projectPathHint: Path): DaemonContext {
        val resolvedMpsHome = resolveMpsHome() ?: throw MpsHomeRequiredException()
        val resolvedJavaHome =
            javaHome?.let(workingDirectory::resolve)
                ?: MpsDistributionLayout.findBundledJavaHome(resolvedMpsHome)
                ?: throw IllegalStateException(
                    "MPS distribution at $resolvedMpsHome does not bundle Java; pass --java-home <path>, " +
                        "or run mops wrapper in a supported Gradle project and then run the wrapper path it prints"
                )

        val projectPath = resolveProjectPath(projectPathHint)

        return DaemonContext.fromLivePaths(
            projectPath = projectPath,
            mpsHome = resolvedMpsHome,
            javaHome = resolvedJavaHome,
            macros = resolveMacros(),
        )
    }

    fun resolveMpsHome(): Path? = mpsHome?.let(workingDirectory::resolve)

    /** The `--macro` values as absolute paths, checked to be directories, since MPS ignores a macro that is not one. */
    fun resolveMacros(): Map<String, Path> = macros.mapValues { (name, value) ->
        require(name !in mpsBuiltInPathMacros) {
            "--macro $name=$value: $name is a path macro MPS defines itself and cannot be set"
        }
        workingDirectory.resolve(value).normalize().also {
            require(it.isDirectory()) { "--macro $name=$value: $it is not a directory" }
        }
    }

    override fun projectPath(start: Path): Path {
        projectRoot?.let { configuredProjectRoot ->
            val resolved = workingDirectory.resolve(configuredProjectRoot).normalize()
            if (resolved.resolve(".mps").isDirectory()) {
                return resolved
            }
            throw ProjectPathNotFoundException(resolved)
        }

        return MopsCommand.resolveProjectPath(start)
    }

    fun resolveProjectPath(start: Path = workingDirectory): Path = projectPath(start)

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
