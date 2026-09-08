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
        "Guess MPS and Java homes from the nearest Gradle build, without starting an MPS daemon.",
        "Supports Specific Languages 2.x defaults and conventional mbeddr RunAntScript tasks.",
        "Uses the build's wrapper and evaluates runtime providers, which may download or extract distributions.",
        "Does not run preparation or language build task actions. Prints a reusable mops command line for POSIX shells.",
    ],
)
class GuessCommandLineCommand(private val root: MopsCommand) : Callable<Int> {
    @Parameters(index = "0", arity = "0..1", paramLabel = "PATH",
        description = ["Directory to inspect (default: --project-root or the working directory)."])
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
