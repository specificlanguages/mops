package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RuntimeHomesTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `a missing MPS home suggests writing the wrapper again`() {
        val project = tempDir.resolve("project").createDirectories()
        val command = MopsCommand(workingDirectory = tempDir).apply {
            mpsHome = "deleted-mps"
            javaHome = tempDir.resolve("deleted-java").pathString
            projectRoot = "project"
        }

        val failure = assertFailsWith<RuntimeHomeNotFoundException> { command.createDaemonContext(tempDir) }

        assertEquals(
            "MPS home does not exist: ${tempDir.resolve("deleted-mps")}. If a mops wrapper passed it, the wrapper " +
                "is out of date: run mops wrapper $project to write it again.",
            failure.message,
        )
    }

    @Test
    fun `a missing Java home suggests writing the wrapper again`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val command = MopsCommand(workingDirectory = tempDir).apply {
            this.mpsHome = mpsHome.pathString
            javaHome = "deleted-java"
        }

        val failure = assertFailsWith<RuntimeHomeNotFoundException> { command.createDaemonContext(tempDir) }

        assertEquals(
            "Java home does not exist: ${tempDir.resolve("deleted-java")}. If a mops wrapper passed it, the wrapper " +
                "is out of date: run mops wrapper $tempDir to write it again.",
            failure.message,
        )
    }
}
