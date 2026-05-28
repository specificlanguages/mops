package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.parallel.ResourceLock
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class ModelGetNodeCommandTest {
    @Test
    fun `model get-node requires a node reference or model target plus node id`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("model", "get-node")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "NODE_REFERENCE")
        assertContains(stderr, "MODEL_TARGET")
        assertContains(stderr, "NODE_ID")
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
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(
                    "com.specificlanguages.json.structure",
                    "2110045694544566904",
                )
        }

        assertEquals(0, exitCode)
        verify(client).getNode(target)
        assertEquals(
            """{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}""" +
                    System.lineSeparator(),
            stdout,
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
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute(
                    nodeReference,
                )
        }

        assertEquals(0, exitCode)
        verify(client).getNode(target)
        assertContains(stdout, """"id":"2110045694544566904"""")
    }
}
