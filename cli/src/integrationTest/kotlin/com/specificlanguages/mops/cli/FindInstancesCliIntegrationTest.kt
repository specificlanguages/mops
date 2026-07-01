package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.GsonCodec
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

@ResourceLock("system-streams")
class FindInstancesCliIntegrationTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `finds concept instances including subconcepts through daemon`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runFindInstances(project, daemonHome, "--json", ABSTRACT_CONCEPT_DECLARATION)

            assertEquals(0, result.exitCode, result.output)
            val response = instancesJson(result)
            assertTrue(response.nodes.isNotEmpty(), result.output)
            assertTrue(response.nodes.all { it.type == "root" }, "concept declarations are roots: ${response.nodes}")
            assertTrue(
                response.nodes.all { it.reference.startsWith("r:") },
                "every node reference should be serialized: ${response.nodes}",
            )
            val concepts = response.nodes.map { it.concept }.toSet()
            assertContains(concepts, CONCEPT_DECLARATION)
            assertContains(concepts, "jetbrains.mps.lang.structure.structure.InterfaceConceptDeclaration")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `exact match excludes subconcept instances`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            // The default search in the preceding test finds subconcept instances of this abstract concept;
            // exact matching requires the direct concept, of which the fixture has none.
            val result = runFindInstances(project, daemonHome, "--json", "--exact", ABSTRACT_CONCEPT_DECLARATION)

            assertEquals(0, result.exitCode, result.output)
            val response = instancesJson(result)
            assertTrue(response.nodes.isEmpty(), "exact match must exclude subconcepts: ${response.nodes}")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `reports an unresolved concept as not found`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runFindInstances(project, daemonHome, "com.specificlanguages.json.structure.DoesNotExist")

            assertTrue(result.exitCode != 0, result.output)
            assertContains(result.output, "concept not found")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `truncates results with a low limit`() {
        val project = copyTestProject("mps-json", tempDir.resolve("mps-json"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()

        try {
            val result = runFindInstances(project, daemonHome, "--json", "--limit", "1", CONCEPT_DECLARATION)

            assertEquals(0, result.exitCode, result.output)
            val response = instancesJson(result)
            assertEquals(1, response.nodes.size, result.output)
            assertTrue(response.truncated, "more concept declarations exist than the limit: ${result.output}")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    private fun runFindInstances(project: Path, daemonHome: Path, vararg args: String): CliResult =
        runCommandLine(
            project,
            "--daemon-home",
            daemonHome.pathString,
            *javaAndMpsHomeArgs(),
            "find",
            "instances",
            *args,
        )

    private fun instancesJson(result: CliResult): FindInstancesResponse =
        GsonCodec.fromJson(result.stdout, FindInstancesResponse::class.java)

    private companion object {
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val ABSTRACT_CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.AbstractConceptDeclaration"
    }
}
