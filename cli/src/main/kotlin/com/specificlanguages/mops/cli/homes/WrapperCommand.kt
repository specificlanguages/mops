package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import com.specificlanguages.mops.protocol.MopsBuild
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import java.nio.file.Path
import java.util.concurrent.Callable

@Command(
    name = "wrapper",
    header = ["Write a project-local mops wrapper from Gradle or given paths."],
    mixinStandardHelpOptions = true,
    description = [
        "Write a project-local mops wrapper using runtime paths discovered from Gradle, or given with --mps-home " +
            "and --java-home.",
        "",
        "Uses the nearest Gradle wrapper to inspect all projects in its build and writes mopsw or mopsw.cmd for " +
            "every discovered MPS project. Pass --project-root to write only that project's wrapper. Wrappers go " +
            "directly in each Gradle project's build directory when it has one detected MPS project; with multiple " +
            "MPS projects, they go in mops/<MPS-project-name> beneath it. Reported paths are relative to the " +
            "working directory.",
        "",
        "When the global options --mps-home and --java-home are both given, mops wrapper uses them and does not " +
            "run Gradle, so the project needs no Gradle build. It writes the wrapper of one MPS project: " +
            "--project-root, or else the project at or above PATH, found the way other commands find it. The " +
            "wrapper goes in the build directory of the MPS project, such as build/mopsw.",
        "",
        "Gradle discovery needs no daemon; Java must be available to run Gradle. Querying configured providers " +
            "may download and extract MPS or JBR distributions; it does not run preparation " +
            "or language build task actions. If discovery finds only some paths, it prints them, writes no " +
            "wrapper, and exits with status 1. When both paths are known, it writes the wrappers and exits with " +
            "status 0, with warnings when the paths are not usable yet, so wrappers can be written before the " +
            "project's preparation steps. Warnings and the reason no wrapper was written go to stderr.",
    ],
    footer = [
        "",
        "Discovery reads mpsDefaults of the com.specificlanguages.mps Gradle plugin, or RunAntScript tasks of the " +
            "de.itemis.mps.gradle.common plugin, in every Gradle project of the build, for the shared MPS and Java " +
            "homes. MPS projects come from configured mpsBuilds entries and .mps project markers in the build. " +
            "Task-action overrides, included builds, and custom Ant property loading are outside this heuristic; " +
            "Specific Languages values are project defaults.",
        "",
        "Each wrapper supplies the shared MPS and Java homes and its MPS project directory, and forwards its " +
            "arguments to mops. It runs the full path of the mops installation that wrote it, so mops need not be " +
            "in PATH. The POSIX wrappers are executable. When an MPS or Java home that a wrapper names no longer " +
            "exists, commands fail and say to run mops wrapper again. --project-root can also precede `wrapper`.",
        "",
        "Provider resolution failures stop discovery and keep Gradle's dependency diagnostics; check the " +
            "configured runtime versions and repositories. If a pinned MPS prerelease is no longer available, " +
            "update the project's MPS prerelease version to the newest available one; prerelease artifacts are " +
            "deleted periodically.",
        "",
        "Path macros: the global option --macro NAME=PATH defines a path macro, such as one that " +
            ".mps/libraries.xml or .mps/modules.xml uses as \$\${NAME}; repeat it for more path macros. PATH is an " +
            "existing directory, absolute or relative to the directory where mops was started. The daemon defines " +
            "the path macros in MPS before it opens the project, and test runs pass them to the test worker. A running " +
            "daemon started with other path macros is not reused: the command fails, and after mops daemon stop the " +
            "next command starts a daemon with the new path macros. Project-relative paths (\$PROJECT_DIR\$) and the " +
            "path macros MPS defines itself (project, mps_home, platform_lib, lib_ext) need no --macro.",
        "",
        "Each wrapper passes the --macro values given to mops wrapper. For a path macro that .mps/libraries.xml or " +
            ".mps/modules.xml uses without a --macro value, mops wrapper guesses the root of the project's Git " +
            "repository, writes it into the wrapper, and prints a warning on stderr; rerun mops wrapper --macro NAME=PATH to " +
            "set another value. Outside a Git repository, the command fails and names the path macro that needs --macro.",
    ],
)
class WrapperCommand(
    private val root: MopsCommand,
    private val mopsExecutable: () -> Path = { mopsExecutablePath() },
) : Callable<Int> {
    @Parameters(
        index = "0", arity = "0..1", paramLabel = "PATH",
        description = [
            "Starting point of discovery, or of the MPS project search with --mps-home and --java-home; default " +
                "is --project-root when supplied, otherwise the working directory.",
        ]
    )
    var path: String? = null

    @Option(
        names = ["--output"],
        paramLabel = "PATH",
        description = ["Exact wrapper file to write when one MPS project is selected, absolute or relative to the directory where mops was started."],
    )
    var output: String? = null

    @Spec
    private lateinit var spec: CommandSpec

    override fun call(): Int {
        spec.commandLine().out.println("mops version: ${MopsBuild.fullVersion}")
        val start = root.workingDirectory.resolve(path ?: root.projectRoot ?: ".").toRealPath()
        val mpsHome = root.mpsHome
        val javaHome = root.javaHome
        val (runtime, targets) = if (mpsHome != null && javaHome != null) {
            givenRuntime(start, mpsHome, javaHome)
        } else {
            discoveredRuntime(start)
        }
        val out = spec.commandLine().out
        val err = spec.commandLine().err
        out.println(
            if (runtime.source == GIVEN_SOURCE) "Source: $GIVEN_SOURCE"
            else "Source: ${runtime.source} in ${displayPath(runtime.projectDir, root.workingDirectory)}"
        )
        out.println("MPS home: ${runtime.mpsHome?.let { displayPath(it, root.workingDirectory) } ?: "unknown"}")
        out.println("Java home: ${runtime.javaHome?.let { displayPath(it, root.workingDirectory) } ?: "unknown"}")
        if (runtime.mpsHome != null && !runtime.usableMps) {
            err.println("Warning: MPS home is missing: ${displayPath(runtime.mpsHome, root.workingDirectory)}")
        }
        if (runtime.javaHome != null && !runtime.usableJava) {
            err.println("Warning: Java home is missing or has no usable bin/java: ${displayPath(runtime.javaHome, root.workingDirectory)}")
        }
        if (!runtime.hasKnownHomes) {
            err.println("Discovery is partial; no wrapper was written. Configure both runtime paths and try again.")
        } else if (targets.isEmpty()) {
            err.println("No MPS projects were found; no wrapper was written.")
        } else {
            writeWrappers(runtime, targets)
        }
        out.flush()
        err.flush()
        return if (runtime.hasKnownHomes && targets.isNotEmpty()) 0 else 1
    }

    /**
     * The given homes and the MPS project found the way other commands find it. The project directory stands in for
     * the Gradle project, so the wrapper goes in its build directory.
     */
    private fun givenRuntime(start: Path, mpsHome: String, javaHome: String): Pair<HomeGuess, List<Pair<HomeGuess, Path>>> {
        val projectRoot = root.resolveProjectPath(start)
        val runtime = HomeGuess(
            projectDir = projectRoot,
            buildDir = projectRoot.resolve("build"),
            source = GIVEN_SOURCE,
            mpsHome = root.workingDirectory.resolve(mpsHome).normalize(),
            javaHome = root.workingDirectory.resolve(javaHome).normalize(),
            mpsProjectRoots = listOf(projectRoot),
        )
        return runtime to listOf(runtime to projectRoot)
    }

    private fun discoveredRuntime(start: Path): Pair<HomeGuess, List<Pair<HomeGuess, Path>>> {
        val guesses = GradleHomeDiscovery().discover(start, spec.commandLine().err)
        val selectedProjectRoot = root.projectRoot?.let { root.resolveProjectPath(start) }
        val targets = if (selectedProjectRoot == null) {
            guesses.flatMap { guess -> guess.mpsProjectRoots.map { guess to it } }
                .distinctBy { it.second }
        } else {
            val owner = guesses.filter { selectedProjectRoot.startsWith(it.projectDir) }
                .maxByOrNull { it.projectDir.nameCount }
                ?: guesses.firstOrNull { selectedProjectRoot in it.mpsProjectRoots }
                ?: error("MPS project is not part of the discovered Gradle build: ${displayPath(selectedProjectRoot, root.workingDirectory)}")
            listOf(owner to selectedProjectRoot)
        }
        val targetRuntime = targets.singleOrNull()?.first?.takeIf { it.source != null }
        val preferredRuntime = targetRuntime?.takeIf { it.hasKnownHomes }
        val runtime = preferredRuntime
            ?: guesses.firstOrNull { it.source != null && it.hasKnownHomes }
            ?: targetRuntime
            ?: guesses.firstOrNull { it.source != null }
            ?: error(
                "No supported runtime configuration found in this Gradle build. " +
                    "Pass --mps-home and --java-home to write the wrapper without Gradle."
            )
        return runtime to targets
    }

    /** Writes a wrapper for each target project; all path macros are settled before the first wrapper is written. */
    private fun writeWrappers(runtime: HomeGuess, targets: List<Pair<HomeGuess, Path>>) {
        val out = spec.commandLine().out
        val windows = System.getProperty("os.name").lowercase().contains("win")
        check(output == null || targets.size == 1) {
            "--output requires --project-root when discovery finds multiple MPS projects."
        }
        val executable = mopsExecutable()
        val givenMacros = root.resolveMacros()
        val macros = targets.associate { (_, projectRoot) ->
            projectRoot to WrapperMacros.complete(projectRoot, givenMacros, spec.commandLine().err)
        }
        targets.forEach { (owner, projectRoot) ->
            val wrapper = output
                ?.let { root.workingDirectory.resolve(it).normalize() }
                ?: defaultWrapperPath(owner, projectRoot, windows)
            val written = runtime.writeWrapper(wrapper, projectRoot, executable, windows, macros.getValue(projectRoot))
            out.println("Project root: ${displayPath(projectRoot, root.workingDirectory)}")
            out.println("Wrapper: ${displayPath(written, root.workingDirectory)}")
        }
    }

    private fun defaultWrapperPath(guess: HomeGuess, projectRoot: Path, windows: Boolean): Path {
        val projectName = projectRoot.fileName.toString()
        val wrapperName = if (windows) "mopsw.cmd" else "mopsw"
        val directory = if (guess.mpsProjectRoots.size <= 1) {
            guess.buildDir
        } else {
            guess.buildDir.resolve("mops").resolve(projectName)
        }
        return directory.resolve(wrapperName)
    }

    private companion object {
        const val GIVEN_SOURCE = "--mps-home and --java-home"
    }
}
