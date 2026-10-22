package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.FindSubconceptsResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import java.nio.file.Path
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
 *
 * The tests share project copies through [SharedDaemons]. None of them leaves a change behind (the edit is rolled
 * back), so they pass in any order.
 */
@ResourceLock("system-streams")
class CliSmokeTest {
    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    lateinit var tempDir: Path

    @Test
    fun `concept hierarchy command round-trips through the daemon`() {
        val project = copyTestProject("base-language-sandbox", tempDir.resolve("base-language-sandbox"))
        val daemonHome = tempDir.resolve("daemon-home").createDirectories()
        try {
            val result = runCommandLine(
                project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(),
                "find", "subconcepts", "jetbrains.mps.baseLanguage.Expression", "--concrete", "--json",
            )
            assertEquals(0, result.exitCode, result.output)
            val response = ProtocolJson.decodeResponse(result.stdout) as FindSubconceptsResponse
            assertContains(response.concepts, "jetbrains.mps.baseLanguage.structure.IntegerConstant")
            assertTrue("jetbrains.mps.baseLanguage.structure.Expression" !in response.concepts)
            assertEquals(response.concepts.sorted().distinct(), response.concepts)
            val direct = runCommandLine(
                project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(),
                "find", "subconcepts", "jetbrains.mps.baseLanguage.Expression", "--direct", "--json",
            )
            assertEquals(0, direct.exitCode, direct.output)
            val directResponse = ProtocolJson.decodeResponse(direct.stdout) as FindSubconceptsResponse
            assertContains(directResponse.concepts, "jetbrains.mps.baseLanguage.structure.BinaryOperation")
            assertTrue("jetbrains.mps.baseLanguage.structure.IntegerConstant" !in directResponse.concepts)
            val program = tempDir.resolve("subconcepts.groovy").also {
                it.writeText("""
                    project.read {
                        def expression = mops.lookup.requireConceptByName('jetbrains.mps.baseLanguage.Expression')
                        assert help(mops.search).contains('mops.search.subConceptsOf')
                        assert !help(mops.lookup).contains('subConceptsOf')
                        def all = mops.search.subConceptsOf(expression)
                        assert all.any { it.qualifiedName == 'jetbrains.mps.baseLanguage.structure.IntegerConstant' }
                        assert !all.contains(expression)
                        def direct = mops.search.subConceptsOf(expression, true, true)
                        assert direct.every { !it.abstract }
                        assert !direct.any { it.qualifiedName == 'jetbrains.mps.baseLanguage.structure.IntegerConstant' }
                    }
                    return 'ok'
                """.trimIndent())
            }
            val code = runCommandLine(
                project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(),
                "code", "run", program.pathString,
            )
            assertEquals(0, code.exitCode, code.output)
            assertEquals("ok", code.stdout.trim())
            val missing = runCommandLine(
                project, "--daemon-home", daemonHome.pathString, *javaAndMpsHomeArgs(),
                "find", "subconcepts", "jetbrains.mps.baseLanguage.NoSuchMopsConcept",
            )
            assertTrue(missing.exitCode != 0, missing.output)
            assertContains(missing.output, "concept \"NoSuchMopsConcept\" was not found")
        } finally {
            stopDaemons(project, daemonHome)
        }
    }

    @Test
    fun `read command round-trips through the daemon`() {
        val result = daemons.project("base-language-sandbox").run("find", "instances", "--json", CLASS_CONCEPT)

        assertEquals(0, result.exitCode, result.output)
        val response = ProtocolJson.decodeResponse(result.stdout) as FindInstancesResponse
        assertTrue(response.nodes.isNotEmpty(), result.output)
    }

    @Test
    fun `failed edit operation reports the violation and leaves the model untouched`() {
        val project = daemons.project("mps-json")
        val model = project.root.resolve(
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps",
        )
        val original = model.readText()
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

        val result = project.run("edit", "model", "--file", batchFile.pathString)

        assertEquals(1, result.exitCode, result.output)
        assertContains(result.stderr, "property not found: doesNotExist")
        assertEquals(original, model.readText())
    }

    @Test
    fun `failing command exits nonzero with the daemon error`() {
        val result = daemons.project("mps-json")
            .run("find", "instances", "com.specificlanguages.json.structure.DoesNotExist")

        assertTrue(result.exitCode != 0, result.output)
        // The fixture language is present but not compiled, so the failure explains the unloaded language rather
        // than reporting a bare "not found".
        assertContains(result.output, "its language \"com.specificlanguages.json\" is not loaded")
        assertContains(result.output, "diagnose module com.specificlanguages.json")
    }

    companion object {
        @JvmField
        @RegisterExtension
        val daemons = SharedDaemons()

        private const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
    }
}
