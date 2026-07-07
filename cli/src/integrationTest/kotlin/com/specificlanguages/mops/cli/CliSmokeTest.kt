package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end smoke tests for the CLI → launcher → daemon → MPS wiring.
 *
 * MPS semantics are covered by the daemon test tier (`daemon/src/test`); these tests only assert
 * that one read, one write-with-rollback, and one failure path survive the full process boundary.
 */
@ResourceLock("system-streams")
class CliSmokeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `read command round-trips through the daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runCommandLine(
                project,
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "find",
                "instances",
                "--json",
                CONCEPT_DECLARATION,
            )

            assertEquals(0, result.exitCode, result.output)
            val response = ProtocolJson.decodeResponse(result.stdout) as FindInstancesResponse
            assertTrue(response.nodes.isNotEmpty(), result.output)
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `failed edit operation reports the violation and leaves the model untouched`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val model = project.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val original = model.readText()
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        val targetReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val batchFile = tempDir.resolve("batch.json")
        batchFile.writeText(
            ProtocolJson.encodeBatch(
                EditBatch(
                    operations = listOf(
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(targetReference),
                            name = "name",
                            value = "ShouldRollback",
                        ),
                        EditOperation.SetProperty(
                            target = EditTarget.NodeReference(targetReference),
                            name = "doesNotExist",
                            value = "boom",
                        ),
                    ),
                ),
            ),
        )

        try {
            val result = runCommandLine(
                project,
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "model",
                "edit",
                "--file",
                batchFile.pathString,
            )

            assertEquals(1, result.exitCode, result.output)
            assertContains(result.stderr, "property not found: doesNotExist")
            assertEquals(original, model.readText())
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `failing command exits nonzero with the daemon error`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runCommandLine(
                project,
                "--daemon-home",
                daemonHome.pathString,
                *javaAndMpsHomeArgs(),
                "find",
                "instances",
                "com.specificlanguages.json.structure.DoesNotExist",
            )

            assertTrue(result.exitCode != 0, result.output)
            assertContains(result.output, "no valid MPS Concept resolved for")
            assertContains(result.output, "com.specificlanguages.json.structure.DoesNotExist")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private companion object {
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
