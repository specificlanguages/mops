package com.specificlanguages.mops.cli.homes

import com.specificlanguages.mops.cli.MopsCommand
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import java.util.concurrent.Callable

@Command(
    name = "wrapper",
    mixinStandardHelpOptions = true,
    description = [
        "Write a project-local mops wrapper using runtime paths discovered from Gradle.",
        "",
        "Uses the nearest Gradle wrapper to inspect `mpsDefaults` of plugin `com.specificlanguages.mps` or `RunAntScript` tasks of plugin `de.itemis.mps.gradle.common`, then writes mopsw or mopsw.cmd in that project's build directory.",
        "No CLI-configured MPS home or daemon is required; Java must be available to run Gradle.",
        "Querying configured providers may download and extract MPS or JBR distributions; it does not run preparation or language build task actions.",
        "Partial discoveries print available values without writing a wrapper and exit with status 1; complete pairs write the wrapper and exit with status 0.",
    ],
)
class WrapperCommand(private val root: MopsCommand) : Callable<Int> {
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
        val guess = discovery.discover(start, spec.commandLine().err)
            ?: error(
                "No supported runtime configuration found in this Gradle build. " +
                    "Pass --mps-home and --java-home directly to other mops commands."
            )
        val out = spec.commandLine().out
        out.println("Source: ${guess.source} in ${guess.projectDir}")
        out.println("MPS home: ${guess.mpsHome ?: "unknown"}")
        out.println("Java home: ${guess.javaHome ?: "unknown"}")
        out.println("Project root: ${guess.mpsProjectRoot ?: "not guessed"}")
        if (guess.mpsHome != null && !guess.usableMps) {
            out.println("MPS directory is missing; run the project's documented runtime preparation first.")
        }
        if (guess.javaHome != null && !guess.usableJava) {
            out.println("Java home has no usable bin/java; run the project's documented runtime preparation first.")
        }
        if (!guess.usableMps || !guess.usableJava) {
            out.println("Discovery is partial; no wrapper was written. Prepare the missing runtime and try again.")
        } else {
            val windows = System.getProperty("os.name").lowercase().contains("win")
            out.println("Wrapper: ${guess.writeWrapper(windows)}")
        }
        out.flush()
        return if (guess.usableMps && guess.usableJava) 0 else 1
    }
}
