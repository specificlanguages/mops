package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText

@Command(
    name = "run",
    description = ["Run a trusted Groovy program. FILE and - both mean stdin when omitted or set to -."],
)
class CodeRunCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "FILE")
    var file: String? = null

    @Option(names = ["--constraints"], description = ["Default edit constraint policy."])
    var constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT

    @Option(names = ["--timeout"], description = ["Hard deadline in seconds; 0 disables it (default: 900)."])
    var timeoutSeconds: Long = 900

    override fun run() {
        require(timeoutSeconds >= 0) { "--timeout must be zero or a positive number of seconds" }
        val sourceName: String
        val source = if (file == null || file == "-") {
            sourceName = file?.takeUnless { it == "-" } ?: "<stdin>"
            System.`in`.bufferedReader().readText()
        } else {
            val path = environment.workingDirectory.resolve(file!!).normalize()
            sourceName = path.toString()
            path.readText()
        }
        val response = environment.daemon().runCode(source, sourceName, constraints, Duration.ofSeconds(timeoutSeconds))
        response.output?.let(::print)
    }
}
