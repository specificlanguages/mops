package com.specificlanguages.mops.cli.testing

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.*
import java.nio.file.Files

@Command(name = "test", description = ["Build and run MPS tests in a separate process."])
class TestCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(arity = "0..*", paramLabel = "TARGET_SEGMENT")
    var target: List<String> = emptyList()
    @Option(names = ["--no-build"], description = ["Use existing compiled output."])
    var noBuild = false
    @Option(names = ["--json"], description = ["Print the structured test report."])
    var json = false
    @Option(names = ["--timeout"], paramLabel = "SECONDS", description = ["Time limit for test execution, counted after build and discovery; default 900, 0 disables it."])
    var timeout: Long = 900
    private var resultCode = 1

    override fun run() {
        require(timeout in 0..Long.MAX_VALUE / 1_000_000) { "invalid timeout" }
        val cancelDir = Files.createTempDirectory("mops-test-cancel-")
        val cancel = cancelDir.resolve("cancel")
        val hook = Thread { Files.writeString(cancel, "cancel") }
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            val report = environment.daemon().runTests(target, !noBuild, timeout * 1000, cancel.toString()).report
            if (json) println(ProtocolJson.encodeTestReport(report)) else {
                val counts = report.results.filter { it.kind == "TEST" }.groupingBy { it.status }.eachCount()
                println("${report.outcome}: ${counts.entries.joinToString { "${it.value} ${it.key.lowercase()}" }}")
                report.diagnostics.forEach { System.err.println(it.lineSequence().first()) }
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
