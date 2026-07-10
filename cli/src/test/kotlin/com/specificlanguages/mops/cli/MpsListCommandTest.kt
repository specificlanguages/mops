package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.MpsListSummaryGroupJson
import com.specificlanguages.mops.protocol.MpsListSummaryJson
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.junit.jupiter.api.parallel.ResourceLock
import picocli.CommandLine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ResourceLock("system-streams")
class MpsListCommandTest {
    @Test
    fun `list prints semantic tree as indented tab-separated text`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1), eq(50), eq(false), isNull())).thenReturn(sampleListResponse())
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute()
        }

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1), eq(50), eq(false), isNull())
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
        whenever(client.list(isNull(), eq(1), eq(50), eq(false), isNull())).thenReturn(response)
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--json")
        }

        assertEquals(0, exitCode)
        verify(client).list(isNull(), eq(1), eq(50), eq(false), isNull())
        assertEquals(response.root, ProtocolJson.decodeListEntry(stdout))
    }

    @Test
    fun `list sends space-separated target segments to daemon`() {
        val client = mock<DaemonClient>()
        whenever(
            client.list(
                eq(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile")),
                eq(1),
                eq(50),
                eq(false),
                isNull(),
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
            eq(50),
            eq(false),
            isNull(),
        )
    }

    @Test
    fun `list forwards summary role and limit flags to daemon`() {
        val client = mock<DaemonClient>()
        whenever(client.list(eq(listOf("JsonFile")), eq(1), eq(10), eq(true), eq("member")))
            .thenReturn(sampleListResponse())

        var exitCode = Int.MIN_VALUE
        tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary", "--role", "member", "--limit", "10", "JsonFile")
        }

        assertEquals(0, exitCode)
        verify(client).list(eq(listOf("JsonFile")), eq(1), eq(10), eq(true), eq("member"))
    }

    @Test
    fun `list renders a summary breakdown instead of children`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1), eq(50), eq(true), isNull())).thenReturn(
            MpsListResponse(
                root = MpsListEntryJson(
                    type = "model",
                    name = "com.example.structure",
                    reference = "r:model",
                    summary = MpsListSummaryJson(
                        by = "concept",
                        groups = listOf(
                            MpsListSummaryGroupJson(key = "a.b.ConceptDeclaration", count = 8),
                            MpsListSummaryGroupJson(key = "a.b.InterfaceConceptDeclaration", count = 1),
                        ),
                    ),
                ),
            ),
        )

        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary")
        }

        assertEquals(0, exitCode)
        assertEquals(
            "model\tcom.example.structure\tr:model" + System.lineSeparator() +
                "  concept\ta.b.ConceptDeclaration\t8" + System.lineSeparator() +
                "  concept\ta.b.InterfaceConceptDeclaration\t1" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `list renders role summary groups with dominant concepts`() {
        val client = mock<DaemonClient>()
        whenever(client.list(eq(listOf("JsonFile")), eq(1), eq(50), eq(true), isNull())).thenReturn(
            MpsListResponse(
                root = MpsListEntryJson(
                    type = "root",
                    name = "JsonFile",
                    concept = "a.b.ConceptDeclaration",
                    reference = "r:model/1",
                    summary = MpsListSummaryJson(
                        by = "role",
                        groups = listOf(
                            MpsListSummaryGroupJson(
                                key = "linkDeclaration",
                                count = 2,
                                concepts = listOf("a.b.LinkDeclaration"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary", "JsonFile")
        }

        assertEquals(0, exitCode)
        assertEquals(
            "root\tJsonFile\ta.b.ConceptDeclaration\tr:model/1" + System.lineSeparator() +
                "  role\tlinkDeclaration\t2\ta.b.LinkDeclaration" + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `list appends a truncation footer carrying shown and total counts`() {
        val client = mock<DaemonClient>()
        whenever(client.list(isNull(), eq(1), eq(2), eq(false), isNull())).thenReturn(
            MpsListResponse(
                root = MpsListEntryJson(
                    type = "model",
                    name = "m",
                    reference = "r:model",
                    childTotal = 5,
                    children = listOf(
                        MpsListEntryJson(type = "root", name = "A", concept = "c", reference = "r:model/1"),
                        MpsListEntryJson(type = "root", name = "B", concept = "c", reference = "r:model/2"),
                    ),
                ),
            ),
        )

        var exitCode = Int.MIN_VALUE
        val stdout = tapSystemOut {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "2")
        }

        assertEquals(0, exitCode)
        assertContains(stdout, "  truncated\t2\t5")
    }

    @Test
    fun `list rejects summary combined with depth before contacting daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary", "--depth", "2")
        }

        assertEquals(1, exitCode)
        assertContains(stderr, "--summary cannot be combined with --depth")
        verifyNoInteractions(client)
    }

    @Test
    fun `list rejects a negative limit before contacting daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(MpsListCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--limit", "-1")
        }

        assertEquals(1, exitCode)
        assertContains(stderr, "limit must not be negative")
        verifyNoInteractions(client)
    }

    @Test
    fun `list rejects slash-bearing target segments before contacting daemon`() {
        val invalidTargets = listOf(
            "com.specificlanguages.json/com.specificlanguages.json.structure",
            "com.specificlanguages.json/",
            "com.specificlanguages.json//com.specificlanguages.json.structure",
            "/tmp/project/model.mps",
        )

        for (invalidTarget in invalidTargets) {
            val client = mock<DaemonClient>()
            var exitCode = Int.MIN_VALUE

            val stderr = tapSystemErr {
                exitCode = CommandLine(MpsListCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute(invalidTarget)
            }

            assertEquals(1, exitCode)
            assertContains(stderr, "target segments must be space-separated")
            verifyNoInteractions(client)
        }
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
