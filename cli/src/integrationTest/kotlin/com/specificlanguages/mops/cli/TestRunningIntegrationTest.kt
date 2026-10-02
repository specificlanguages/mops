package com.specificlanguages.mops.cli

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
    fun `zero tests is a saved discovery failure and daemon remains usable`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("project"))
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val result = cli("test", "--no-build", "--json")
            assertEquals(1, result.exitCode, result.output)
            val report = ProtocolJson.decodeTestReport(result.stdout)
            assertEquals("DISCOVERY_FAILED", report.outcome, result.output)
            assertTrue(report.diagnostics.any { it.contains("No tests discovered") }, result.output)
            assertEquals(report, ProtocolJson.decodeTestReport(Path.of(report.reportPath).readText()))
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
            val method = cli("test", requireNotNull(passed.source), "--no-build", "--json")
            assertEquals(0, method.exitCode, method.output)
            assertEquals(1, ProtocolJson.decodeTestReport(method.stdout).results.count { it.kind == "TEST" }, method.output)
            val script = tempDir.resolve("testing.groovy")
            script.writeText("""
                def module = project.read { mops.lookup.requireModule('mops.tests') }
                def model = project.read { mops.lookup.requireModel('mops.tests.nativecases@tests') }
                def node = project.read { mops.lookup.requireNode('${passed.source}') }
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
            assertContains(ProtocolJson.decodeTestReport(invalid.stdout).diagnostics.joinToString(), "not a runnable test")
            val modelFile = project.resolve("solutions/tests/models/ordinary.mps")
            modelFile.writeText(modelFile.readText().replace("~RuntimeException.&lt;init&gt;(java.lang.String)", "~MissingClass.&lt;init&gt;(java.lang.String)"))
            val build = cli("test", "--json")
            assertEquals(1, build.exitCode, build.output)
            assertEquals("BUILD_FAILED", ProtocolJson.decodeTestReport(build.stdout).outcome, build.output)
            assertTrue(ProtocolJson.decodeTestReport(build.stdout).results.isEmpty())
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `timeouts cancellation and enclosing Code Mode deadline retain completed results`() {
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
            val timed = cli("test", "mops.tests", ".slow", "SlowTest", "--no-build", "--json", "--timeout", "90")
            assertEquals(1, timed.exitCode, timed.output)
            val report = ProtocolJson.decodeTestReport(timed.stdout)
            assertEquals("TIMED_OUT", report.outcome, timed.output)
            assertFalse(report.complete)
            assertTrue(report.results.any { it.kind == "TEST" && it.status == "PASSED" }, timed.output)
            assertEquals(0, cli("daemon", "ping").exitCode)
            val prior = reports().map { it.reportPath }.toSet()
            val stdout = tempDir.resolve("cancel-stdout.txt")
            val stderr = tempDir.resolve("cancel-stderr.txt")
            val install = Path.of(System.getProperty("test.cliInstall"))
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").pathString,
                "-cp", install.resolve("lib/*").pathString, "com.specificlanguages.mops.cli.MainKt",
                "--project-root", project.pathString, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(),
                "test", "mops.tests", ".slow", "SlowTest", "--no-build", "--timeout", "0", "--json",
            ).redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start()
            try {
                awaitCondition { reports().any { it.reportPath !in prior && it.results.any { result -> result.kind == "TEST" && result.status == "PASSED" } } }
                process.destroy()
                val finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)
                val result = CliResult(if (finished) process.exitValue() else -1, stdout.readText(), stderr.readText())
                if (result.stderr.isNotEmpty()) System.err.print(result.stderr)
                assertTrue(finished, result.output)
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
            val code = cli("code", "run", script.pathString, "--timeout", "90")
            assertEquals(1, code.exitCode, code.output)
            assertContains(code.stderr, "daemon was terminated")
            awaitCondition { reports().any { it.reportPath !in beforeCode && it.outcome == "PARENT_TERMINATED" } }
            val partial = reports().single { it.reportPath !in beforeCode }
            assertFalse(partial.complete)
            assertTrue(partial.results.any { it.kind == "TEST" && it.status == "PASSED" })
            assertEquals(0, cli("daemon", "ping").exitCode)
            val startup = cli("test", "--timeout", "1", "--json")
            assertEquals(1, startup.exitCode, startup.output)
            assertEquals("TIMED_OUT", ProtocolJson.decodeTestReport(startup.stdout).outcome, startup.output)
        } finally { stopDaemons(project, home) }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120)
        while (!condition()) {
            check(System.nanoTime() < deadline) { "Timed out waiting for a test report update" }
            Thread.sleep(100)
        }
    }
}
