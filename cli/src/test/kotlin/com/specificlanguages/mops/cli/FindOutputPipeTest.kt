package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.protocol.DaemonRecordStore
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.PongResponse
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Runs `find` through the whole CLI against a prerecorded daemon to check the stdout contract a shell pipeline relies
 * on: in text mode every non-`#` line is a result row, and with `--refs-only` every line is a node reference.
 */
@ResourceLock("system-streams")
class FindOutputPipeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `text output has exactly one non-metadata line per result when truncated`() {
        val response = truncatedResponse()
        val output = runFind(response, "find", "instances", "--limit", "2", CONCEPT)

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals(response.nodes.size, output.stdout.lines().filterNot { it.startsWith("#") }.size, output.stdout)
        assertEquals(1, output.stdout.lines().count { it.startsWith("# truncated: ") }, output.stdout)
    }

    @Test
    fun `refs-only output is only node references when truncated`() {
        val response = truncatedResponse()
        val output = runFind(response, "find", "instances", "--refs-only", "--limit", "2", CONCEPT)

        assertEquals(0, output.exitCode, output.stderr)
        assertEquals(response.nodes.map { it.reference }, output.stdout.lines())
        assertTrue(output.stderr.lines().any { it.startsWith("truncated\t2\t") }, output.stderr)
    }

    private class CliOutput(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runFind(response: FindInstancesResponse, vararg args: String): CliOutput {
        val project = tempDir.mpsProject()
        val daemonHome = tempDir.resolve("daemon-home")
        val mpsHome = tempDir.mpsHome()
        val javaHome = Path.of(System.getProperty("java.home"))
        val daemon = startPrerecordedDaemon(
            PongResponse(projectPath = project.pathString, mpsHome = mpsHome.pathString, workspacePath = "/project"),
            response,
        )
        DaemonRecordStore.forDaemonHome(daemonHome).write(
            daemonRecord(port = daemon.port, project = project, mpsHome = mpsHome, workspace = Path.of("/project")),
        )

        var exitCode = Int.MIN_VALUE
        var stdout = ""
        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                exitCode = newCommandLine(workingDirectory = project).execute(
                    "--daemon-home", daemonHome.pathString,
                    "--mps-home", mpsHome.pathString,
                    "--java-home", javaHome.pathString,
                    *args,
                )
            }
        }
        daemon.join(5_000)
        return CliOutput(exitCode, stdout.trimEnd('\r', '\n'), stderr)
    }

    private fun truncatedResponse() = instancesResponse(nodes = listOf(testNode, testChildNode), limit = 2, truncated = true)

    private companion object {
        const val CONCEPT = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
