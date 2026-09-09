package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import java.util.concurrent.Callable

@Command(
    name = "guess-command-line",
    mixinStandardHelpOptions = true,
    description = [
        "Uses the nearest Gradle wrapper to inspect `mpsDefaults` of plugin `com.specificlanguages.mps` or `RunAntScript` tasks of plugin `de.itemis.mps.gradle.common`, then prints a command line for direct use.",
        "No MPS home or daemon is required; Java must be available to run Gradle.",
        "Discovery may or may not cause MPS and the JBR to be downloaded and extracted or any other Gradle tasks to be executed.",
        "Partial discoveries print available arguments and exit with status 1; complete pairs exit with status 0.",
    ],
)
class GuessCommandLineCommand(private val root: MopsCommand) : Callable<Int> {
    @Parameters(
        index = "0", arity = "0..1", paramLabel = "PATH",
        description = ["Starting point of discovery; default is --project-root when supplied, otherwise the working directory."]
    )
    var path: String? = null

    @Spec
    private lateinit var spec: CommandSpec

    override fun call(): Int {
        val start = root.workingDirectory.resolve(path ?: root.projectRoot ?: ".").toRealPath()
        val discovery = GradleHomeDiscovery()
        val guess = discovery.select(discovery.discover(start, spec.commandLine().err), start)
            ?: error("No supported runtime configuration found in this Gradle build. Configure --mps-home and --java-home explicitly.")
        val out = spec.commandLine().out
        out.println("Source: ${guess.source} in ${guess.projectDir}")
        out.println("MPS home: ${guess.mpsHome ?: "unknown"}")
        out.println("Java home: ${guess.javaHome ?: "unknown"}")
        guess.diagnostics.forEach { out.println(it) }
        if (guess.mpsHome != null && !guess.usableMps) {
            out.println("MPS directory is missing; run the project's documented runtime preparation first.")
        }
        if (guess.javaHome != null && !guess.usableJava) {
            out.println("Java home has no usable bin/java; run the project's documented runtime preparation first.")
        }
        if (guess.usableMps || guess.usableJava) {
            out.println("Command line (POSIX shell): ${guess.commandLine()}")
        }
        if (!guess.usableMps || !guess.usableJava) {
            out.println("Discovery is partial; supply the remaining home explicitly.")
        }
        out.flush()
        return if (guess.usableMps && guess.usableJava) 0 else 1
    }
}
