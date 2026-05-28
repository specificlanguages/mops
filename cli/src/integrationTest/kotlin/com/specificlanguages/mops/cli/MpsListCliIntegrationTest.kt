package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ResourceLock("system-streams")
class MpsListCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `lists current project entity through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "--depth", "0")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("project", root.type)
            assertEquals("mps-json", root.name)
            assertEquals(null, root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists project modules through daemon by default`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runListCommand(project, daemonHome, "ls", "--json")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            val modules = assertNotNull(root.children)
            val language = modules.single { it.name == "com.specificlanguages.json" }
            assertEquals("module", language.type)
            assertEquals("language", language.moduleKind)
            assertEquals("f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)", language.reference)
            assertNull(language.children)

            val solution = modules.single { it.name == "com.specificlanguages.json.build" }
            assertEquals("module", solution.type)
            assertEquals("solution", solution.moduleKind)
            assertEquals("84f0ad52-c7ca-45dd-99c5-9605c96bf808(com.specificlanguages.json.build)", solution.reference)
            assertNull(solution.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists repository entity through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runList(project, daemonHome, "--json", "--depth", "0", "/")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("repository", root.type)
            assertEquals("/", root.name)
            assertNull(root.children)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `lists loaded repository modules through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runListCommand(project, daemonHome, "ls", "--json", "/")

            assertEquals(0, result.exitCode, result.output)
            val root = GsonCodec.fromJson(result.stdout, MpsListEntryJson::class.java)
            assertEquals("repository", root.type)
            val modules = assertNotNull(root.children)
            assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json" })
            assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json.build" })
            assertNotNull(modules.singleOrNull { it.name == "MPS.Core" })
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runList(project: Path, daemonHome: Path, vararg args: String): CliResult =
        runListCommand(project, daemonHome, "list", *args)

    private fun runListCommand(project: Path, daemonHome: Path, command: String, vararg args: String): CliResult =
        runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            command,
            *args,
        )
}
