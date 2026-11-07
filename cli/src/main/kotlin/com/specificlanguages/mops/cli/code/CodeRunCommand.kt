package com.specificlanguages.mops.cli.code

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

@Command(
    name = "run",
    header = ["Run a trusted Groovy program from FILE or stdin."],
    description = ["Run a trusted Groovy program. FILE and - both mean stdin when omitted or set to -."],
    footer = [
        "",
        "The mops distribution includes a pinned Groovy core runtime. The daemon uses MPS's Groovy runtime when " +
            "present and the bundled core otherwise; no additional JAR or classpath override is required. See " +
            "`mops explain code`.",
    ],
)
class CodeRunCommand(private val environment: CommandEnvironment) : CliCommand() {
    @Parameters(index = "0", arity = "0..1", paramLabel = "FILE")
    var file: String? = null

    @Option(names = ["--timeout"], description = ["Hard deadline in seconds; 0 disables it (default: 900)."])
    var timeoutSeconds: Long = 900

    override fun run() {
        require(timeoutSeconds >= 0) { "--timeout must be zero or a positive number of seconds" }
        val sourceName: String
        val source = if (file == null || file == "-") {
            sourceName = "<stdin>"
            System.`in`.bufferedReader().readText()
        } else {
            val path = environment.workingDirectory.resolve(file!!).normalize()
            sourceName = path.toString()
            path.readText()
        }
        val response = environment.daemon().runCode(source, sourceName, Duration.ofSeconds(timeoutSeconds))
        response.notes.forEach(System.err::println)
        response.output?.let(::print)
    }
}
