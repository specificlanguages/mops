package com.specificlanguages.mops.cli.homes

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class MopsExecutablePathTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `executable belongs to the running distribution`() {
        val installation = temporary.resolve("mops installation")
        val lib = installation.resolve("lib").createDirectories()
        val bin = installation.resolve("bin").createDirectories()
        val jar = lib.resolve("cli.jar").apply { writeText("") }
        listOf(false to "mops", true to "mops.bat").forEach { (windows, name) ->
            val executable = bin.resolve(name).apply { writeText("") }
            assertEquals(executable, mopsExecutablePath(jar, windows))
        }
    }

    @Test
    fun `development classes use the installed distribution`() {
        val build = temporary.resolve("build")
        val classes = build.resolve("classes/kotlin/main").createDirectories()
        val executable = build.resolve("install/mops/bin").createDirectories().resolve("mops")
        executable.writeText("")
        assertEquals(executable, mopsExecutablePath(classes, windows = false))
    }
}
