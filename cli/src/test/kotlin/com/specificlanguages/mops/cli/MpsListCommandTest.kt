package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.GsonCodec
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
import kotlin.test.Test
import kotlin.test.assertEquals

class MpsListCommandTest {
    @Test
    fun `list prints semantic tree as indented tab-separated text`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1))).thenReturn(sampleListResponse())
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

    @Test
    fun `list prints semantic tree as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleListResponse()
        whenever(client.list(isNull(), eq(1))).thenReturn(response)
        val stdout = ByteArrayOutputStream()

        val exitCode = CommandLine(MpsListCommand(client))
            .setExecutionExceptionHandler(PrintErrorAndExit)
            .also { it.out = PrintWriter(stdout, true) }
            .execute("--json")

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1))
        assertEquals(response.root, GsonCodec.fromJson(stdout.toString(), MpsListEntryJson::class.java))
    }

    private fun sampleListResponse() =
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
        )
}
