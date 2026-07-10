package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelRenderNodeResponse
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
class ModelRenderNodeCommandTest {
    @Test
    fun `model render-node requires a node reference or model target plus node id`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("model", "render-node")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "NODE_REFERENCE")
    }

    @Test
    fun `model render-node prints the rendered text for model target and node id`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.InModel(modelTarget = "json.sandbox", nodeId = "4Twci\$d7zxq")
        whenever(client.renderNode(target, false)).thenReturn(ModelRenderNodeResponse("{\n  \"foo\": 1\n}"))
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelRenderNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("json.sandbox", "4Twci\$d7zxq")
        }

        assertEquals(0, exitCode)
        verify(client).renderNode(target, false)
        assertEquals("{\n  \"foo\": 1\n}" + System.lineSeparator(), stdout)
    }

    @Test
    fun `model render-node passes the allow-reflective flag to the daemon`() {
        val client = mock<DaemonClient>()
        val nodeReference = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)/4Twci\$d7zxq"
        val target = NodeTarget.NodeReference(nodeReference)
        whenever(client.renderNode(target, true)).thenReturn(ModelRenderNodeResponse("json object { }"))
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(ModelRenderNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--allow-reflective", nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).renderNode(target, true)
    }
}
