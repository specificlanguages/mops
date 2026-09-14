package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import java.nio.file.Path
import java.util.concurrent.Callable

@Command(
    name = "wrapper",
    mixinStandardHelpOptions = true,
    description = [
        "Write a project-local mops wrapper using runtime paths discovered from Gradle.",
        "",
        "Uses the nearest Gradle wrapper to inspect all projects in its build and writes mopsw or mopsw.cmd for every discovered MPS project. Pass --project-root to write only that project's wrapper.",
        "No CLI-configured MPS home or daemon is required; Java must be available to run Gradle.",
        "Querying configured providers may download and extract MPS or JBR distributions; it does not run preparation or language build task actions.",
        "Partial discoveries print available values without writing a wrapper and exit with status 1; known pairs write the wrapper and exit with status 0, with warnings when their paths are not usable yet.",
    ],
)
class WrapperCommand(private val root: MopsCommand) : Callable<Int> {
    @Parameters(
        index = "0", arity = "0..1", paramLabel = "PATH",
        description = ["Starting point of discovery; default is --project-root when supplied, otherwise the working directory."]
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
        val start = root.workingDirectory.resolve(path ?: root.projectRoot ?: ".").toRealPath()
        val discovery = GradleHomeDiscovery()
        val guesses = discovery.discover(start, spec.commandLine().err)
        val selectedProjectRoot = root.projectRoot?.let { root.resolveProjectPath(start) }
        val targets = if (selectedProjectRoot == null) {
            guesses.flatMap { guess -> guess.mpsProjectRoots.map { guess to it } }
                .distinctBy { it.second }
        } else {
            val owner = guesses.filter { selectedProjectRoot.startsWith(it.projectDir) }
                .maxByOrNull { it.projectDir.nameCount }
                ?: guesses.firstOrNull { selectedProjectRoot in it.mpsProjectRoots }
                ?: error("MPS project is not part of the discovered Gradle build: $selectedProjectRoot")
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
                    "Pass --mps-home and --java-home directly to other mops commands."
            )
        val out = spec.commandLine().out
        out.println("Source: ${runtime.source} in ${runtime.projectDir}")
        out.println("MPS home: ${runtime.mpsHome ?: "unknown"}")
        out.println("Java home: ${runtime.javaHome ?: "unknown"}")
        if (runtime.mpsHome != null && !runtime.usableMps) {
            out.println("Warning: MPS home is missing: ${runtime.mpsHome}")
        }
        if (runtime.javaHome != null && !runtime.usableJava) {
            out.println("Warning: Java home is missing or has no usable bin/java: ${runtime.javaHome}")
        }
        if (!runtime.hasKnownHomes) {
            out.println("Discovery is partial; no wrapper was written. Configure both runtime paths and try again.")
        } else if (targets.isEmpty()) {
            out.println("No MPS projects were found; no wrapper was written.")
        } else {
            val windows = System.getProperty("os.name").lowercase().contains("win")
            check(output == null || targets.size == 1) {
                "--output requires --project-root when discovery finds multiple MPS projects."
            }
            targets.forEach { (owner, projectRoot) ->
                val wrapper = output
                    ?.let { root.workingDirectory.resolve(it).normalize() }
                    ?: defaultWrapperPath(owner, projectRoot, windows)
                out.println("Project root: $projectRoot")
                out.println("Wrapper: ${runtime.writeWrapper(wrapper, projectRoot, windows)}")
            }
        }
        out.flush()
        return if (runtime.hasKnownHomes && targets.isNotEmpty()) 0 else 1
    }

    private fun defaultWrapperPath(guess: HomeGuess, projectRoot: Path, windows: Boolean): Path {
        val projectName = projectRoot.fileName.toString()
        val wrapperName = if (windows) "mopsw.cmd" else "mopsw"
        return guess.buildDir.resolve("mops").resolve(projectName).resolve(wrapperName)
    }
}
