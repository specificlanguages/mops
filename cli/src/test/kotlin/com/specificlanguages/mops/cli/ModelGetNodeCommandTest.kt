package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import org.junit.jupiter.api.io.TempDir
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ModelGetNodeCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `model get-node requires a node reference or model target plus node id`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine().also {
            it.err = PrintWriter(stderr, true)
        }.execute("model", "get-node")

        assertEquals(2, exitCode)
        assertContains(stderr.toString(), "Missing required parameter")
        assertContains(stderr.toString(), "NODE_REFERENCE")
        assertContains(stderr.toString(), "MODEL_TARGET")
        assertContains(stderr.toString(), "NODE_ID")
    }

    @Test
    fun `model get-node prints json node export for model target and node id`() {
        val project = tempDir.mpsProject()
        val pool = RecordingPool()
        pool.getNodeResponse = ModelGetNodeResponse(
            node = mapOf(
                "model" to "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                "concept" to "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                "id" to "2110045694544566904",
            ),
        )
        val stdout = ByteArrayOutputStream()
        val mpsHome = tempDir.mpsHome()
        val javaHome = tempDir.javaHome()

        val exitCode = CommandLine(MopsCommand(workingDirectory = project, daemonPool = pool))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute(
                "--mps-home", mpsHome.toString(),
                "--java-home", javaHome.toString(),
                "model", "get-node",
                "com.specificlanguages.json.structure",
                "2110045694544566904",
            )

        assertEquals(0, exitCode)
        assertEquals("com.specificlanguages.json.structure", pool.getNodeModelTarget)
        assertEquals("2110045694544566904", pool.getNodeNodeId)
        assertNull(pool.getNodeNodeReference)
        assertEquals(
            """{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}""" +
                    System.lineSeparator(),
            stdout.toString(),
        )
    }

    @Test
    fun `model get-node accepts a serialized node reference`() {
        val project = tempDir.mpsProject()
        val pool = RecordingPool()
        pool.getNodeResponse = ModelGetNodeResponse(
            node = mapOf(
                "model" to "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                "concept" to "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                "id" to "2110045694544566904",
            ),
        )
        val stdout = ByteArrayOutputStream()
        val mpsHome = tempDir.mpsHome()
        val javaHome = tempDir.javaHome()
        val nodeReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        val exitCode = CommandLine(MopsCommand(workingDirectory = project, daemonPool = pool))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute(
                "--mps-home", mpsHome.toString(),
                "--java-home", javaHome.toString(),
                "model", "get-node",
                nodeReference,
            )

        assertEquals(0, exitCode)
        assertNull(pool.getNodeModelTarget)
        assertNull(pool.getNodeNodeId)
        assertEquals(nodeReference, pool.getNodeNodeReference)
        assertContains(stdout.toString(), """"id":"2110045694544566904"""")
    }
}
