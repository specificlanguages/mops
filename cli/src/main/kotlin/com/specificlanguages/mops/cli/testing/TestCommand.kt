package com.specificlanguages.mops.cli.testing

import com.specificlanguages.mops.cli.common.CliCommand
import com.specificlanguages.mops.cli.common.CommandEnvironment
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import picocli.CommandLine.*
import java.nio.file.Files

@Command(
    name = "test",
    description = ["Build and run MPS tests in a separate process."],
    footer = [
        "",
        "The target selects the project (omit it), a module, a model, or a root test case; individual test methods " +
            "are unsupported. The default build includes the owning modules and their required dependencies; " +
            "--no-build requires existing compiled classes. Ctrl-C cancels the worker. Cancelling a CLI run and " +
            "test timeouts leave the daemon usable.",
        "",
        "Execution uses the distribution's Ant `launchtests` machinery in IDEA test mode, including its test " +
            "session, failure detection, and XML reports. The test JVM's classpath is built independently of the " +
            "daemon's boot classpath. The worker's module repository holds the project modules and the folders of " +
            "the project libraries (.mps/libraries.xml) as the daemon loaded them, and the worker gets the path " +
            "macros the daemon defines, including those given with --macro. A project library whose path uses a " +
            "macro the daemon does not define is left out, with a diagnostic.",
        "",
        "When the worker cannot load classes of some modules, for example because a dependency is absent from its " +
            "repository, the report's diagnostics list the modules from the worker's log. Plugins are discovered " +
            "automatically. On MPS 2025.1 only, Images (com.intellij.platform.images) is disabled to avoid its SVG " +
            "parser classloader defect; tests requiring Images are unsupported on that version. Model and test-case " +
            "filtering happens after module-wide class discovery, so broken unselected classes can also fail a run, " +
            "particularly with --no-build. A matching JBR with javac is required to compile the version-specific " +
            "adapter.",
        "",
        "Every run saves report.json under the daemon workspace's test-runs/<run-id>/ directory and updates it as " +
            "results arrive. Worker output is saved next to it in worker.log (preparation) and ant.log (test " +
            "execution); XML reports are in xml-reports/. The report retains tests, containers, skips, assumption " +
            "aborts, failures, and source node references.",
        "",
        "Text output prints an `<outcome>: <N> passed, <N> failed, ...` summary line and a `Report: <path>` line " +
            "on stdout, and diagnostics such as a failed discovery or a crashed test worker on stderr, one line " +
            "each. --json prints the report, with the diagnostics in its `diagnostics` field. No discovered tests, " +
            "preparation failures, test or container failures, and incomplete runs exit 1; a run that discovers no " +
            "tests reports which selection resolved to nothing (the project, or the module, model, or root test " +
            "case named by the target). Skips and assumption aborts alone do not fail a run.",
        "",
        "In Code Mode, mops.testing.run(selection, options) runs the same operation outside model-access blocks; " +
            "the selection is the native project, module, model, or test node, and the options default to " +
            "[build: true, timeout: 900]. A failed report does not change the Code Mode exit status. The enclosing Code Mode deadline still terminates its " +
            "daemon; the test worker detects that and saves an incomplete report. Project operations are serialized " +
            "during a run, and changes are saved before launch. Tests use the existing checkout and may change its " +
            "files.",
    ],
)
class TestCommand(private val environment: CommandEnvironment) : CliCommand(), IExitCodeGenerator {
    constructor(daemonClient: DaemonClient) : this(DaemonClientCommandEnvironment(daemonClient))

    @Parameters(
        arity = "0..*",
        paramLabel = "TARGET_SEGMENT",
        description = [
            "Navigation target segments of a module, model, or root test case, or its serialized reference. Omit " +
                "for the project.",
        ],
    )
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
            if (json) println(ProtocolJson.testReport.encode(report)) else {
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
