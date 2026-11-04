package com.specificlanguages.mops.cli

import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class LibraryModelRefreshIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `external edits of a library model outside the project folder are visible in the same daemon and after a restart`() {
        val project = copyTestProject("project-library", tempDir.resolve("project"))
        val shared = Files.move(project.resolve("libs"), tempDir.resolve("shared"))
        val libraries = project.resolve(".mps/libraries.xml")
        libraries.writeText(libraries.readText().replace("${'$'}PROJECT_DIR${'$'}/libs", "${'$'}PROJECT_DIR${'$'}/../shared"))
        val libraryModel = shared.resolve("library/models/values.mps")
        val projectModel = project.resolve("solutions/tests/models/library@tests.mps")
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val program = tempDir.resolve("names.groovy").also {
            it.writeText("""
                project.read {
                    ['mops.library.values', 'mops.librarytests.library@tests'].collect { name ->
                        mops.lookup.requireModel(name).rootNodes.collect { it.name }.findAll().join(',')
                    }.join(';')
                }
            """.trimIndent())
        }
        fun run(vararg args: String): CliResult =
            runCommandLine(project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(), *args)
        fun names(): String {
            val result = run("code", "run", program.pathString)
            assertEquals(0, result.exitCode, result.output)
            return result.stdout.trim()
        }
        fun rename(model: Path, from: String, to: String) {
            val original = model.readText()
            val edited = original.replace("value=\"$from\"", "value=\"$to\"")
            check(edited != original) { "expected to rename $from in $model" }
            model.writeText(edited)
        }

        try {
            assertEquals("LibraryValue;LibraryPass", names())

            rename(projectModel, "LibraryPass", "LibraryPassEdited")
            assertEquals("LibraryValue;LibraryPassEdited", names(), "project model edit in the same daemon")

            rename(libraryModel, "LibraryValue", "LibraryValueEdited")
            assertEquals("LibraryValueEdited;LibraryPassEdited", names(), "library model edit in the same daemon")

            assertEquals(0, run("daemon", "stop").exitCode)
            rename(libraryModel, "LibraryValueEdited", "LibraryValueAfterRestart")
            assertEquals("LibraryValueAfterRestart;LibraryPassEdited", names(), "edit while the daemon was stopped")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }
}
