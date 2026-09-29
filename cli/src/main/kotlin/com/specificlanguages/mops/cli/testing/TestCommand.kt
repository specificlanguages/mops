package com.specificlanguages.mops.cli.testing

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.*
import java.nio.file.Files

@Command(name = "test", description = ["Build and run MPS tests in a separate process."])
class TestCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    @Parameters(arity = "0..*", paramLabel = "TARGET_SEGMENT")
    var target: List<String> = emptyList()
    @Option(names = ["--no-build"], description = ["Use existing compiled output."])
    var noBuild = false
    @Option(names = ["--json"], description = ["Print the structured test report."])
    var json = false
    @Option(names = ["--timeout"], paramLabel = "SECONDS", description = ["Overall deadline; default 900, 0 disables it."])
    var timeout: Long = 900
    private var resultCode = 1

    override fun run() {
        require(timeout >= 0 && timeout <= Long.MAX_VALUE / 1000 - System.currentTimeMillis()) { "invalid timeout" }
        val deadline = if (timeout == 0L) 0 else System.currentTimeMillis() + timeout * 1000
        val cancelDir = Files.createTempDirectory("mops-test-cancel-")
        val cancel = cancelDir.resolve("cancel")
        val hook = Thread { Files.writeString(cancel, "cancel") }
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            val report = environment.daemon().runTests(target, !noBuild, deadline, cancel.toString()).report
            if (json) println(ProtocolJson.encodeTestReport(report)) else {
                val counts = report.results.filter { it.kind == "TEST" }.groupingBy { it.status }.eachCount()
                println("${report.outcome}: ${counts.entries.joinToString { "${it.value} ${it.key.lowercase()}" }}")
                report.diagnostics.forEach { println(it.lineSequence().first()) }
                println("Report: ${report.reportPath}")
            }
            resultCode = if (report.successful) 0 else 1
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook)
            Files.deleteIfExists(cancel)
            Files.deleteIfExists(cancelDir)
        }
    }
    override fun getExitCode() = resultCode
}
