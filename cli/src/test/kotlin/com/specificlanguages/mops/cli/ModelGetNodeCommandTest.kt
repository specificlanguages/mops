package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ModelGetNodeCommandTest {
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
        val client = mock<DaemonClient>()
        val target = NodeTarget.InModel(
            modelTarget = "com.specificlanguages.json.structure",
            nodeId = "2110045694544566904",
        )
        whenever(client.getNode(target)).thenReturn(
            ModelGetNodeResponse(
                node = MpsNodeJson(
                    model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    id = "2110045694544566904",
                ),
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = CommandLine(ModelGetNodeCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute(
                "com.specificlanguages.json.structure",
                "2110045694544566904",
            )

        assertEquals(0, exitCode)
        verify(client).getNode(target)
        assertEquals(
            """{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}""" +
                    System.lineSeparator(),
            stdout.toString(),
        )
    }

    @Test
    fun `model get-node accepts a serialized node reference`() {
        val client = mock<DaemonClient>()
        val nodeReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val target = NodeTarget.NodeReference(nodeReference)
        whenever(client.getNode(target)).thenReturn(
            ModelGetNodeResponse(
                node = MpsNodeJson(
                    model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    id = "2110045694544566904",
                ),
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = CommandLine(ModelGetNodeCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute(
                nodeReference,
            )

        assertEquals(0, exitCode)
        verify(client).getNode(target)
        assertContains(stdout.toString(), """"id":"2110045694544566904"""")
    }
}
