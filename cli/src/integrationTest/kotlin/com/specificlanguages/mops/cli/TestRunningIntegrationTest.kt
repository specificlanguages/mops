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
    fun `native cases build and run with individual source results`() {
        val project = copyTestingProject()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val result = cli("test", "mops.tests.nativecases@tests", "--json")
            assertEquals(1, result.exitCode, result.output)
            val report = ProtocolJson.decodeTestReport(result.stdout)
            assertEquals("TEST_FAILED", report.outcome, result.output)
            assertEquals(setOf("PASSED", "FAILED"), report.results.filter { it.kind == "TEST" }.map { it.status }.toSet(), result.output)
            assertTrue(report.results.filter { it.kind == "TEST" }.all { it.source != null }, result.output)
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `ordinary JUnit and legacy tests retain skips aborts and setup failures`() {
        val project = copyTestingProject()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val pass = cli("test", "mops.tests", ".ordinary", "OrdinaryPass", "--json")
            assertEquals(0, pass.exitCode, pass.output)
            val report = ProtocolJson.decodeTestReport(pass.stdout)
            assertEquals(setOf("PASSED", "SKIPPED", "ABORTED"), report.results.filter { it.kind == "TEST" }.map { it.status }.toSet(), pass.output)
            val legacy = cli("test", "mops.tests", ".ordinary", "LegacyTest", "--no-build", "--json")
            assertEquals(0, legacy.exitCode, legacy.output)
            assertEquals(1, ProtocolJson.decodeTestReport(legacy.stdout).results.count { it.kind == "TEST" && it.status == "PASSED" }, legacy.output)
            val parameterized = cli("test", "mops.tests", ".ordinary", "ParameterizedCase", "parameterized", "--no-build", "--json")
            assertEquals(0, parameterized.exitCode, parameterized.output)
            val invocations = ProtocolJson.decodeTestReport(parameterized.stdout).results.filter { it.kind == "TEST" }
            assertEquals(2, invocations.size, parameterized.output)
            assertEquals(2, invocations.map { it.id }.toSet().size)
            assertTrue(invocations.all { it.source != null && it.status == "PASSED" }, parameterized.output)
            val setup = cli("test", "mops.tests", ".ordinary", "ContainerFailure", "--no-build", "--json")
            assertEquals(1, setup.exitCode, setup.output)
            assertEquals("TEST_FAILED", ProtocolJson.decodeTestReport(setup.stdout).outcome, setup.output)
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `language and generator tests execute with MPS fixtures`() {
        val project = copyTestingProject()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            for (model in listOf("mops.tests.language@tests", "mops.tests.generator@tests")) {
                val result = cli("test", model, "--json")
                assertEquals(0, result.exitCode, result.output)
                assertTrue(ProtocolJson.decodeTestReport(result.stdout).results.any { it.kind == "TEST" && it.status == "PASSED" }, result.output)
            }
        } finally { stopDaemons(project, home) }
    }

    @Test
    fun `selection levels preparation errors and Code Mode share the test runner`() {
        val project = copyTestingProject()
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
        val project = copyTestingProject()
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
            val output = tempDir.resolve("cancel-output.txt")
            val install = Path.of(System.getProperty("test.cliInstall"))
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").pathString,
                "-cp", install.resolve("lib/*").pathString, "com.specificlanguages.mops.cli.MainKt",
                "--project-root", project.pathString, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(),
                "test", "mops.tests", ".slow", "SlowTest", "--no-build", "--timeout", "0", "--json",
            ).redirectErrorStream(true).redirectOutput(output.toFile()).start()
            try {
                awaitCondition { reports().any { it.reportPath !in prior && it.results.any { result -> result.kind == "TEST" && result.status == "PASSED" } } }
                process.destroy()
                assertTrue(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS), output.readText())
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

    private fun copyTestingProject(): Path {
        val project = copyTestProject("testing", tempDir.resolve("project"))
        val mpsHome = Path.of(System.getProperty("test.mpsHome"))
        // MPS 2026.2 packages the Jupiter API stubs in JUnit; parameterized-test stubs remain in org.junit.junit5.
        if (mpsHome.resolve("lib/intellij.libraries.junit5.jar").exists()) {
            for (name in listOf("ordinary", "slow", "crash")) {
                val model = project.resolve("solutions/tests/models/$name.mps")
                model.writeText(model.readText().replace(
                    "63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.api(org.junit.junit5/)",
                    "49808fad-9d41-4b96-83fa-9231640f6b2b/java:org.junit.jupiter.api(JUnit/)",
                ))
            }
        }
        return project
    }

}
