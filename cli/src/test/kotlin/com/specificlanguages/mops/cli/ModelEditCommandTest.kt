package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.ProtocolJson
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class ModelEditCommandTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `model edit reads batch from stdin and prints user-facing response payload`() {
        val client = mock<DaemonClient>()
        val batch = sampleBatch()
        val response = ModelEditResponse(created = emptyMap(), violations = emptyList())
        whenever(client.modelEdit(batch)).thenReturn(response)
        var exitCode = Int.MIN_VALUE
        var stdout = ""

        withTextFromSystemIn(ProtocolJson.encodeBatch(batch)).execute {
            stdout = tapSystemOut {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute()
            }
        }

        assertEquals(0, exitCode)
        verify(client).modelEdit(batch)
        assertEquals(response, ProtocolJson.decodeResponse(stdout))
    }

    @Test
    fun `model edit reads batch from file`() {
        val client = mock<DaemonClient>()
        val batch = sampleBatch()
        val response = ModelEditResponse(created = emptyMap(), violations = emptyList())
        whenever(client.modelEdit(batch)).thenReturn(response)
        val batchFile = tempDir.resolve("batch.json").apply {
            writeText(ProtocolJson.encodeBatch(batch))
        }
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(ModelEditCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--file", batchFile.toString())
        }

        assertEquals(0, exitCode)
        verify(client).modelEdit(batch)
    }

    @Test
    fun `model edit rejects empty batch before daemon dispatch`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        withTextFromSystemIn("""{"operations":[]}""").execute {
            tapSystemOut {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute()
            }
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    @Test
    fun `model edit rejects malformed batch before daemon dispatch`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        withTextFromSystemIn("""{"operations":[""").execute {
            tapSystemOut {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute()
            }
        }

        assertEquals(1, exitCode)
        verifyNoInteractions(client)
    }

    private fun sampleBatch(): EditBatch =
        EditBatch(
            operations = listOf(
                EditOperation.SetProperty(
                    target = EditTarget.NodeReference(
                        "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904",
                    ),
                    name = "name",
                    value = "RenamedConcept",
                ),
            ),
        )
}
