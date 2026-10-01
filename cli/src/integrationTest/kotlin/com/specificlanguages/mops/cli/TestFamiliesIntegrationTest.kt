package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.TestRunReport
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

@ResourceLock("system-streams")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestFamiliesIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    // Only completed reports are shared. No test method receives a live daemon or a mutable project.
    private val reports: Pair<TestRunReport, TestRunReport> by lazy {
        val project = copyTestingProject(tempDir.resolve("project"))
        project.resolve("solutions/tests/models/slow.mps").deleteExisting()
        project.resolve("solutions/tests/models/crash.mps").deleteExisting()
        val home = tempDir.resolve("daemon-home").createDirectories()
        fun cli(vararg args: String) = runCommandLine(project, "--daemon-home", home.pathString, *javaAndMpsHomeArgs(), *args)
        try {
            val all = cli("test", "--json")
            assertEquals(1, all.exitCode, all.output)
            val allReport = ProtocolJson.decodeTestReport(all.stdout)
            assertEquals("TEST_FAILED", allReport.outcome, all.output)
            assertTrue(allReport.complete, all.output)
            val parameterized = cli("test", "mops.tests", ".ordinary", "ParameterizedCase", "--no-build", "--json")
            assertEquals(0, parameterized.exitCode, parameterized.output)
            allReport to ProtocolJson.decodeTestReport(parameterized.stdout)
        } finally {
            stopDaemons(project, home)
        }
    }

    @Test
    fun `native cases build and run with individual source results`() {
        val native = reports.first.results.filter { it.kind == "TEST" && it.className?.startsWith("mops.tests.nativecases.") == true }
        assertEquals(setOf("PASSED", "FAILED"), native.map { it.status }.toSet(), reports.first.toString())
        assertTrue(native.all { it.source != null }, native.toString())
    }

    @Test
    fun `ordinary JUnit and legacy tests retain skips aborts and setup failures`() {
        val all = reports.first
        val ordinary = all.results.filter { it.kind == "TEST" && it.className?.endsWith(".OrdinaryPass") == true }
        assertEquals(setOf("PASSED", "SKIPPED", "ABORTED"), ordinary.map { it.status }.toSet(), all.toString())
        val legacy = all.results.filter { it.kind == "TEST" && it.className?.endsWith(".LegacyTest") == true }
        assertEquals(listOf("PASSED"), legacy.map { it.status }, all.toString())
        assertTrue(all.results.any { it.className?.endsWith(".ContainerFailure") == true && it.status == "FAILED" }, all.toString())
        val invocations = reports.second.results.filter { it.kind == "TEST" }
        assertEquals(2, invocations.size, reports.second.toString())
        assertEquals(2, invocations.map { it.id }.toSet().size)
        assertTrue(invocations.all { it.source != null && it.status == "PASSED" }, invocations.toString())
    }

    @Test
    fun `language and generator tests execute with MPS fixtures`() {
        for (model in listOf("mops.tests.language@tests", "mops.tests.generator@tests")) {
            assertTrue(reports.first.results.any {
                it.kind == "TEST" && it.source?.contains("($model)") == true && it.status == "PASSED"
            }, reports.first.toString())
        }
    }
}
