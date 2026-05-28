package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListResponse
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import kotlin.test.assertContains
import kotlin.test.Test
import kotlin.test.assertEquals

class MpsListCommandTest {
    @Test
    fun `mops list is a top-level daemon-backed command`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine()
            .also { it.err = PrintWriter(stderr, true) }
            .execute("list")

        assertEquals(1, exitCode)
        assertContains(stderr.toString(), "MPS home is required")
    }

    @Test
    fun `list prints semantic tree as indented tab-separated text`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1))).thenReturn(
            MpsListResponse(
                root = MpsListEntryJson(
                    type = "project",
                    name = "mps-json",
                    children = listOf(
                        MpsListEntryJson(
                            type = "module",
                            name = "com.specificlanguages.json",
                            moduleKind = "language",
                            reference = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)",
                        ),
                    ),
                ),
            ),
        )
        val stdout = ByteArrayOutputStream()

        val exitCode = CommandLine(MpsListCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute()

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1))
        assertEquals(
            "project\tmps-json" + System.lineSeparator() +
                "  language\tcom.specificlanguages.json\tf3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)" +
                System.lineSeparator(),
            stdout.toString(),
        )
    }
}
