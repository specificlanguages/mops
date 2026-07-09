package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.github.stefanbirkner.systemlambda.SystemLambda.withTextFromSystemIn
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ChildPosition
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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
    fun `model edit passes the --constraints mode to the daemon`() {
        val client = mock<DaemonClient>()
        val batch = sampleBatch()
        val response = ModelEditResponse(created = emptyMap(), violations = emptyList())
        whenever(client.modelEdit(batch, ConstraintEnforcement.STRICT)).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        withTextFromSystemIn(ProtocolJson.encodeBatch(batch)).execute {
            tapSystemOut {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute("--constraints", "strict")
            }
        }

        assertEquals(0, exitCode)
        verify(client).modelEdit(batch, ConstraintEnforcement.STRICT)
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
    fun `model edit parses structural operations from stdin and forwards them`() {
        val client = mock<DaemonClient>()
        val model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        val batch = EditBatch(
            operations = listOf(
                EditOperation.AddChild(
                    target = EditTarget.NodeReference("$model/1"),
                    role = "propertyDeclaration",
                    concept = "jetbrains.mps.lang.structure.structure.PropertyDeclaration",
                    properties = listOf(MpsNodePropertyJson(name = "name", value = "added")),
                ),
                EditOperation.MoveAsChild(
                    target = EditTarget.NodeReference("$model/2"),
                    into = EditTarget.NodeReference("$model/3"),
                    role = "propertyDeclaration",
                    position = ChildPosition.First,
                ),
                EditOperation.Delete(target = EditTarget.NodeReference("$model/4")),
            ),
        )
        whenever(client.modelEdit(batch)).thenReturn(ModelEditResponse(created = emptyMap(), violations = emptyList()))
        var exitCode = Int.MIN_VALUE

        withTextFromSystemIn(ProtocolJson.encodeBatch(batch)).execute {
            tapSystemOut {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute()
            }
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

    @Test
    fun `model edit reports unknown op with derived list and explain pointer, no daemon`() {
        val stderr = assertRejected(
            """{"operations":[{"op":"addNode","target":"m/1"}]}""",
        )
        assertContains(stderr, """operations[0]: unknown op "addNode"""")
        assertContains(
            stderr,
            "supported: addChild, addRoot, copyAsChild, copyAsRoot, delete, deleteChild, moveAsChild, moveAsRoot, " +
                "replace, setProperty, setReference",
        )
        assertContains(stderr, """Did you mean "addRoot"?""")
        assertContains(stderr, "See: mops explain edit")
    }

    @Test
    fun `model edit reports missing required field with op explain pointer, no daemon`() {
        val stderr = assertRejected(
            """{"operations":[{"op":"copyAsChild","target":"m/1","source":"m/2"}]}""",
        )
        assertContains(stderr, """operations[0]: copyAsChild requires "role" — see: mops explain edit.copyAsChild""")
    }

    @Test
    fun `model edit reports unknown field with op explain pointer, no daemon`() {
        val stderr = assertRejected(
            """{"operations":[{"op":"copyAsChild","target":"m/1","source":"m/2","role":"r","roel":"x"}]}""",
        )
        assertContains(stderr, """operations[0]: copyAsChild has unknown field "roel" — see: mops explain edit.copyAsChild""")
    }

    @Test
    fun `model edit reports invalid target with target explain pointer, no daemon`() {
        val stderr = assertRejected(
            """{"operations":[{"op":"moveAsChild","target":"m/1","into":{"bogus":true},"role":"r"}]}""",
        )
        assertContains(stderr, """operations[0]: moveAsChild field "into" is not a valid target — see: mops explain target""")
    }

    @Test
    fun `model edit reports invalid position with position explain pointer, no daemon`() {
        val stderr = assertRejected(
            """{"operations":[{"op":"addChild","target":"m/1","role":"r","concept":"c","position":"middle"}]}""",
        )
        assertContains(stderr, """operations[0]: addChild field "position" is not a valid position — see: mops explain position""")
    }

    @Test
    fun `model edit reports batch-shape error with edit explain pointer, no daemon`() {
        val stderr = assertRejected("""[1,2,3]""")
        assertContains(stderr, """edit batch must be a JSON object with an "operations" array — see: mops explain edit""")
    }

    // Drives the command with bad batch text and asserts it exits non-zero without touching the daemon, returning stderr.
    private fun assertRejected(badJson: String): String {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE
        var stderr = ""
        withTextFromSystemIn(badJson).execute {
            stderr = tapSystemErr {
                exitCode = CommandLine(ModelEditCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute()
            }
        }
        assertNotEquals(0, exitCode)
        verifyNoInteractions(client)
        return stderr
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
