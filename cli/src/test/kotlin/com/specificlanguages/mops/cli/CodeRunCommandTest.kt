package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.code.CodeRunCommand
import com.specificlanguages.mops.cli.common.DaemonClientCommandEnvironment
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.CodeResultResponse
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import picocli.CommandLine
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class CodeRunCommandTest {
    @Test
    fun `code run prints the result on stdout and each note on stderr`(@TempDir tempDir: Path) {
        val program = tempDir.resolve("program.groovy").apply { writeText("project.command { 1 }") }
        val notes = listOf(
            "added used language exprs to model sandbox.m1",
            "added model import sandbox.m2 to model sandbox.m1",
        )
        val client = mock<DaemonClient>()
        whenever(client.runCode(any(), any(), any())).thenReturn(CodeResultResponse("1", notes))

        var stdout = ""
        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                val command = CommandLine(CodeRunCommand(DaemonClientCommandEnvironment(client)))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                assertEquals(0, command.execute(program.pathString))
            }
        }

        assertEquals("1", stdout)
        assertEquals(notes.joinToString("") { it + System.lineSeparator() }, stderr)
    }
}
