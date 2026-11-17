package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.DaemonPaths
import com.specificlanguages.mops.protocol.DaemonWorkspace
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Tag("smoke")
@ResourceLock("system-streams")
class LibraryModelRefreshIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `external edits of a library model outside the project folder are visible in the same daemon and after a restart`() {
        val project = prepareProject()
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        exerciseEdits(project, daemonHome) { args ->
            runCommandLine(project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(), *args.toTypedArray())
        }
        if (watcherExpected) {
            assertContains(daemonLogs(daemonHome), "refreshed external changes using file watcher events")
        }
    }

    @Test
    fun `external edits are visible with the native file watcher turned off`() {
        val project = prepareProject()
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val cli = InstalledCli(
            project, daemonHome, tempDir,
            mapOf("MOPS_DAEMON_JAVA_OPTIONS" to "-Didea.filewatcher.disabled=true"),
        )
        exerciseEdits(project, daemonHome) { args -> cli.run(*args.toTypedArray()) }
        val logs = daemonLogs(daemonHome)
        assertContains(logs, "using a full walk because the file watcher is not operational")
        assertFalse("using file watcher events" in logs, logs)
    }

    /** Copies the library fixture and moves its library folder out of the project folder. */
    private fun prepareProject(): Path {
        val project = copyTestProject("project-library", tempDir.resolve("project"))
        Files.move(project.resolve("libs"), tempDir.resolve("shared"))
        val libraries = project.resolve(".mps/libraries.xml")
        libraries.writeText(libraries.readText().replace("${'$'}PROJECT_DIR${'$'}/libs", "${'$'}PROJECT_DIR${'$'}/../shared"))
        return project
    }

    private fun exerciseEdits(project: Path, daemonHome: Path, run: (List<String>) -> CliResult) {
        val libraryModel = tempDir.resolve("shared/library/models/values.mps")
        val projectModel = project.resolve("solutions/tests/models/library@tests.mps")
        val program = tempDir.resolve("names.groovy").also {
            it.writeText("""
                project.read {
                    ['mops.library.values', 'mops.librarytests.library@tests'].collect { name ->
                        mops.lookup.requireModel(name).rootNodes.collect { it.name }.findAll().join(',')
                    }.join(';')
                }
            """.trimIndent())
        }
        fun names(): String {
            val result = run(listOf("code", "run", program.pathString))
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

            assertEquals(0, run(listOf("daemon", "stop")).exitCode)
            rename(libraryModel, "LibraryValueEdited", "LibraryValueAfterRestart")
            assertEquals("LibraryValueAfterRestart;LibraryPassEdited", names(), "edit while the daemon was stopped")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun daemonLogs(daemonHome: Path): String =
        DaemonPaths(daemonHome).projects.listDirectoryEntries()
            .map { DaemonWorkspace(it).logFile() }.filter { it.isRegularFile() }.joinToString("\n") { it.readText() }

    private companion object {
        // Every supported MPS version ships fsnotifier for macOS and for Linux on x86-64.
        val watcherExpected = System.getProperty("os.name").let { os ->
            os.startsWith("Mac") || os.startsWith("Linux") && System.getProperty("os.arch") in setOf("amd64", "x86_64")
        }
    }
}
