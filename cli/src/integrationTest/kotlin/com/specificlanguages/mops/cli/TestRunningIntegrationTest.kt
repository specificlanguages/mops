package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
class TestRunningIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `zero tests is a saved discovery failure naming the selection and daemon remains usable`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val result = cli("test", "--no-build", "--json")
            assertEquals(1, result.exitCode, result.output)
            val report = ProtocolJson.decodeTestReport(result.stdout)
            assertEquals("DISCOVERY_FAILED", report.outcome, result.output)
            assertContains(report.diagnostics, "No tests discovered for selection: project (no target given)")
            assertEquals(report, ProtocolJson.decodeTestReport(Path.of(report.reportPath).readText()))
            val model = cli("test", "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)", "--no-build", "--json")
            assertEquals(1, model.exitCode, model.output)
            val modelReport = ProtocolJson.decodeTestReport(model.stdout)
            assertEquals("DISCOVERY_FAILED", modelReport.outcome, model.output)
            assertContains(modelReport.diagnostics, "No tests discovered for selection: model baselanguage.sandbox")
            assertEquals(0, cli("daemon", "ping").exitCode)
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `selection levels preparation errors and Code Mode share the test runner`() {
        val project = copyTestingProject(tempDir.resolve("project"))
        project.resolve("solutions/tests/models/slow.mps").deleteExisting()
        project.resolve("solutions/tests/models/crash.mps").deleteExisting()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val missing = cli("test", "mops.tests.nativecases@tests", "--no-build", "--json")
            assertEquals(1, missing.exitCode, missing.output)
            assertEquals("DISCOVERY_FAILED", ProtocolJson.decodeTestReport(missing.stdout).outcome, missing.output)
            val all = cli("test", "--json")
            assertEquals(1, all.exitCode, all.output)
            val report = ProtocolJson.decodeTestReport(all.stdout)
            assertEquals("TEST_FAILED", report.outcome, all.output)
            val passed = report.results.first { it.kind == "TEST" && it.className?.contains("NativePass") == true }
            val caseSource = report.results.first { it.kind == "CONTAINER" && it.className?.contains("NativePass") == true }.source
            val testCase = cli("test", requireNotNull(caseSource), "--no-build", "--json")
            assertEquals(0, testCase.exitCode, testCase.output)
            assertEquals(1, ProtocolJson.decodeTestReport(testCase.stdout).results.count { it.kind == "TEST" }, testCase.output)
            val script = tempDir.resolve("testing.groovy")
            script.writeText("""
                def module = project.read { mops.lookup.requireModule('mops.tests') }
                def model = project.read { mops.lookup.requireModel('mops.tests.nativecases@tests') }
                def node = project.read {
                    def root = mops.lookup.requireNode('${passed.source}')
                    while (root.parent != null) root = root.parent
                    root
                }
                def reports = [project, module, model, node].collect { mops.testing.run(it, [build:false, timeout:120]) }
                assert reports*.outcome == ['TEST_FAILED', 'TEST_FAILED', 'TEST_FAILED', 'SUCCESS']
                try { project.read { mops.testing.run(node) }; assert false } catch (IllegalStateException expected) {}
                return reports.last()
            """.trimIndent())
            val code = cli("code", "run", script.pathString)
            assertEquals(0, code.exitCode, code.output)
            assertTrue(ProtocolJson.decodeTestReport(code.stdout).successful, code.output)
            val invalid = cli("test", "r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)/4098", "--no-build", "--json")
            assertEquals(1, invalid.exitCode, invalid.output)
            assertContains(ProtocolJson.decodeTestReport(invalid.stdout).diagnostics.joinToString(), "Individual test methods are unsupported")
            val modelFile = project.resolve("solutions/tests/models/ordinary.mps")
            modelFile.writeText(modelFile.readText().replace("~RuntimeException.&lt;init&gt;(java.lang.String)", "~MissingClass.&lt;init&gt;(java.lang.String)"))
            val build = cli("test", "--json")
            assertEquals(1, build.exitCode, build.output)
            assertEquals("BUILD_FAILED", ProtocolJson.decodeTestReport(build.stdout).outcome, build.output)
            assertTrue(ProtocolJson.decodeTestReport(build.stdout).results.isEmpty())
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `timeouts cancellation and a terminated daemon retain completed results`() {
        val project = copyTestingProject(tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        fun reports() = home.resolve("projects").listDirectoryEntries().flatMap { workspace ->
            val runs = workspace.resolve("test-runs")
            if (runs.exists()) runs.listDirectoryEntries().map { it.resolve("report.json") }
                .filter { it.exists() }.map { ProtocolJson.decodeTestReport(it.readText()) }
            else emptyList()
        }
        try {
            val warm = cli("test", "mops.tests", ".ordinary", "OrdinaryPass", "--json", "--timeout", "0")
            assertEquals(0, warm.exitCode, warm.output)
            val warmReport = ProtocolJson.decodeTestReport(warm.stdout)
            assertEquals("SUCCESS", warmReport.outcome, warm.output)
            assertTrue(warmReport.results.any { it.kind == "TEST" && it.status == "PASSED" }, warm.output)
            assertTrue(warmReport.timingsMillis.keys.containsAll(listOf(
                "preparationStartup", "build", "antStartupAndDiscovery", "execution", "antShutdown", "workerLifetime",
            )), warmReport.toString())
            assertTrue(warmReport.timingsMillis.values.all { it >= 0 }, warmReport.toString())
            val run = Path.of(warmReport.reportPath).parent
            val workerLog = run.resolve("ant.log").readText()
            assertContains(workerLog, "MOPS_TEST_MODE=USUAL")
            assertFalse(workerLog.contains("SvgParserDefinition"), workerLog)
            val disabled = run.resolve("test-config/disabled_plugins.txt")
            if (Path.of(System.getProperty("test.mpsHome")).fileName.toString() == "2025.1.4")
                assertContains(disabled.readText(), "com.intellij.platform.images")
            else assertFalse(disabled.exists(), "Images must remain available outside 2025.1")
            assertFalse(workerLog.contains("Language with ID 'SVG' is already registered"), "SVG language conflict; see ${warmReport.reportPath}")
            // The limit counts from the start of test execution: aCompleted passes within it, zSlow sleeps 600 seconds.
            val timed = cli("test", "mops.tests", ".slow", "SlowTest", "--no-build", "--json", "--timeout", "15")
            assertEquals(1, timed.exitCode, timed.output)
            val report = ProtocolJson.decodeTestReport(timed.stdout)
            assertEquals("TIMED_OUT", report.outcome, timed.output)
            assertFalse(report.complete)
            assertTrue(report.results.any { it.kind == "TEST" && it.status == "PASSED" }, timed.output)
            assertEquals(0, cli("daemon", "ping").exitCode)
            val prior = reports().map { it.reportPath }.toSet()
            val process = startCli(project, home, "cancel", "test", "mops.tests", ".slow", "SlowTest", "--no-build", "--timeout", "0", "--json")
            try {
                awaitCondition { reports().any { it.reportPath !in prior && it.results.any { result -> result.kind == "TEST" && result.status == "PASSED" } } }
                process.destroy()
                val result = awaitExit(process, "cancel")
                assertNotEquals(-1, result.exitCode, result.output)
                awaitCondition { reports().any { it.reportPath !in prior && it.outcome == "CANCELLED" } }
                val cancelled = reports().single { it.reportPath !in prior }
                assertFalse(cancelled.complete)
                assertTrue(cancelled.results.any { it.kind == "TEST" && it.status == "PASSED" })
            } finally { if (process.isAlive) process.destroyForcibly() }
            assertEquals(0, cli("daemon", "ping").exitCode)
            val crashed = cli("test", "mops.tests.crash", "--no-build", "--json")
            assertEquals(1, crashed.exitCode, crashed.output)
            val crashedReport = ProtocolJson.decodeTestReport(crashed.stdout)
            assertEquals("WORKER_FAILED", crashedReport.outcome, crashed.output)
            assertFalse(crashedReport.complete)
            assertTrue(crashedReport.results.any { it.kind == "TEST" && it.status == "PASSED" }, crashed.output)
            val beforeCode = reports().map { it.reportPath }.toSet()
            val script = tempDir.resolve("deadline.groovy")
            script.writeText("""
                def model = project.read { mops.lookup.requireModel('mops.tests.slow') }
                return mops.testing.run(model, [build:false, timeout:0])
            """.trimIndent())
            // Kill the daemon once a test has passed, the way an expired `code run --timeout` does
            // (DefaultDaemonClientTest covers that the client kills it). The worker must record what completed.
            val daemonPid = requireNotNull(DaemonRecordStore.forDaemonHome(home).read(project)).record.pid
            val code = startCli(project, home, "code", "code", "run", script.pathString, "--timeout", "0")
            try {
                awaitCondition { reports().any { it.reportPath !in beforeCode && it.results.any { result -> result.kind == "TEST" && result.status == "PASSED" } } }
                ProcessHandle.of(daemonPid).ifPresent(ProcessHandle::destroyForcibly)
                val result = awaitExit(code, "code")
                assertEquals(1, result.exitCode, result.output)
                awaitCondition { reports().any { it.reportPath !in beforeCode && it.outcome == "PARENT_TERMINATED" } }
                val partial = reports().single { it.reportPath !in beforeCode }
                assertFalse(partial.complete)
                assertTrue(partial.results.any { it.kind == "TEST" && it.status == "PASSED" })
            } finally { if (code.isAlive) code.destroyForcibly() }
            assertEquals(0, cli("daemon", "ping").exitCode)
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `project inventory runs both test modules and module selection runs only one`() {
        val project = copyTestingProject(tempDir.resolve("project"))
        val peer = project.resolve("solutions/peer").createDirectories()
        peer.resolve("models").createDirectories()
        val descriptor = project.resolve("solutions/tests/tests.msd").readText()
            .replace("mops.tests", "mops.peer").replace("85ebbead-a656-46af-af88-81c850c5e554", "e305bffa-ec1b-440d-b031-1c4d0ad04fef")
        peer.resolve("peer.msd").writeText(descriptor)
        peer.resolve("models/native@tests.mps").writeText(project.resolve("solutions/tests/models/native@tests.mps").readText()
            .replace("mops.tests", "mops.peer").replace("df8fc5ad-b32f-4109-bfab-79b959f29474", "ac179a29-1ec7-4711-a7c5-0e7854318e17"))
        val inventory = project.resolve(".mps/modules.xml")
        inventory.writeText(inventory.readText().replace("</projectModules>",
            "<modulePath path=\"\$PROJECT_DIR\$/solutions/peer/peer.msd\" folder=\"\" /></projectModules>"))
        project.resolve("solutions/tests/models/slow.mps").deleteExisting()
        project.resolve("solutions/tests/models/crash.mps").deleteExisting()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val all = cli("test", "--json")
            val report = ProtocolJson.decodeTestReport(all.stdout)
            assertEquals("TEST_FAILED", report.outcome, all.output)
            val namespaces = report.results.mapNotNull { it.className }.map { it.substringBeforeLast('.') }.toSet()
            assertTrue("mops.tests.nativecases" in namespaces, all.output)
            assertTrue("mops.peer.nativecases" in namespaces, all.output)
            val selected = cli("test", "mops.peer", "--no-build", "--json")
            val peerReport = ProtocolJson.decodeTestReport(selected.stdout)
            assertEquals("TEST_FAILED", peerReport.outcome, selected.output)
            val cases = peerReport.results.filter { it.kind == "TEST" }
            assertEquals(2, cases.size, selected.output)
            assertTrue(cases.all { it.className?.startsWith("mops.peer.") == true }, selected.output)
        } finally { stopDaemons(project, home) }
    }

    // Starts the installed CLI without waiting for it, so a test can interrupt the command while it runs.
    private fun startCli(project: Path, home: Path, name: String, vararg args: String): Process {
        val install = Path.of(System.getProperty("test.cliInstall"))
        return ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").pathString,
            "-cp", install.resolve("lib/*").pathString, "com.specificlanguages.mops.cli.MainKt",
            "--project-root", project.pathString, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args,
        ).redirectOutput(tempDir.resolve("$name-stdout.txt").toFile())
            .redirectError(tempDir.resolve("$name-stderr.txt").toFile())
            .start()
    }

    // Exit code -1 means the CLI did not exit within 10 seconds.
    private fun awaitExit(process: Process, name: String): CliResult {
        val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
        val result = CliResult(
            if (finished) process.exitValue() else -1,
            tempDir.resolve("$name-stdout.txt").readText(),
            tempDir.resolve("$name-stderr.txt").readText(),
        )
        if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
        return result
    }

    // Worker startup and discovery alone can take about 90 seconds on CI runners shared by two integration-test JVMs.
    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(300)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for a test report update" }
            Thread.sleep(100)
        }
    }
}
