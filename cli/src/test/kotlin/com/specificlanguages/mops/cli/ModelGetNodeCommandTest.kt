package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.PongResponse
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.nio.file.Path
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
        val client = RecordingGetNodeClient()
        client.response = ModelGetNodeResponse(
            node = MpsNodeJson(
                model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                id = "2110045694544566904",
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
        assertEquals(
            NodeTarget.InModel(
                modelTarget = "com.specificlanguages.json.structure",
                nodeId = "2110045694544566904",
            ),
            client.target,
        )
        assertEquals(
            """{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}""" +
                    System.lineSeparator(),
            stdout.toString(),
        )
    }

    @Test
    fun `model get-node accepts a serialized node reference`() {
        val client = RecordingGetNodeClient()
        client.response = ModelGetNodeResponse(
            node = MpsNodeJson(
                model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                id = "2110045694544566904",
            ),
        )
        val stdout = ByteArrayOutputStream()
        val nodeReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        val exitCode = CommandLine(ModelGetNodeCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute(
                nodeReference,
            )

        assertEquals(0, exitCode)
        assertEquals(NodeTarget.NodeReference(nodeReference), client.target)
        assertContains(stdout.toString(), """"id":"2110045694544566904"""")
    }

    private class RecordingGetNodeClient : DaemonClient {
        lateinit var response: ModelGetNodeResponse
        var target: NodeTarget? = null

        override fun ping(): PongResponse = throw UnsupportedOperationException()
        override fun resave(modelTarget: Path): ModelResaveResponse = throw UnsupportedOperationException()
        override fun list(target: String?, depth: Int): MpsListResponse = throw UnsupportedOperationException()

        override fun getNode(target: NodeTarget): ModelGetNodeResponse {
            this.target = target
            return response
        }
    }
}
