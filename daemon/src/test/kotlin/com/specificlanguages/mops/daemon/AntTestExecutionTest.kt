package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.writeText
import kotlin.test.assertContains

class AntTestExecutionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `the test worker loads JNA from lib-jna of a distribution without architecture folders`() {
        val mps = directory.resolve("mps")
        mps.resolve("plugins").createDirectories()
        mps.resolve("lib/jna").createDirectories().resolve("libjnidispatch.jnilib").createFile()
        mps.resolve("build.properties").writeText("mpsBootstrapCore.version=2026.1.2\n")
        val run = directory.resolve("run").createDirectories()
        val execution = AntTestExecution(mps, run, directory.resolve("system"), TestReportStore(run.resolve("report.json")),
            TestRunLimits(null, 0))
        val selection = AntTestSelection(
            testModules = emptyList(), testModuleIds = emptyList(), repositoryModules = emptyList(), repositoryFolders = emptyList(),
            macros = emptyMap(), model = null, node = null, description = "project", diagnostics = emptyList(),
        )

        val jvmArgs = execution.testJvmArgs(directory.resolve("project"), selection, run, run, run)

        assertContains(jvmArgs, "-Djna.boot.library.path=${mps.resolve("lib/jna")}")
    }
}
