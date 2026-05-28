package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.GsonCodec
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListResponse
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.parallel.ResourceLock
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class MpsListCommandTest {
    @Test
    fun `list prints semantic tree as indented tab-separated text`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1))).thenReturn(sampleListResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute()
        }

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1))
        assertEquals(
            "project\tmps-json" + System.lineSeparator() +
                "  language\tcom.specificlanguages.json\tf3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `list prints semantic tree as json when requested`() {
        val client = mock<DaemonClient>()
        val response = sampleListResponse()
        whenever(client.list(isNull(), eq(1))).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json")
        }

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1))
        assertEquals(response.root, GsonCodec.fromJson(stdout, MpsListEntryJson::class.java))
    }

    @Test
    fun `list sends space-separated target segments to daemon`() {
        val client = mock<DaemonClient>()
        whenever(
            client.list(
                eq(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile")),
                eq(1),
            ),
        ).thenReturn(sampleListResponse())
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile")
        }

        assertEquals(0, exitCode)
        verify(client).list(
            eq(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile")),
            eq(1),
        )
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
