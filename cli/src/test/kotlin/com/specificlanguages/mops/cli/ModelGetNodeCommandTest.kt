package com.specificlanguages.mops.cli

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr
import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import com.specificlanguages.mops.cli.get.ModelGetNodeCommand
import com.specificlanguages.mops.daemoncomms.DaemonClient
import com.specificlanguages.mops.cli.output.renderJson
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelGetNodeSummaryResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.parallel.ResourceLock
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import picocli.CommandLine

@ResourceLock("system-streams")
class ModelGetNodeCommandTest {
    @Test
    fun `model get-node requires a node reference or model target plus node ID`() {
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = newCommandLine().execute("get", "node")
        }

        assertEquals(2, exitCode)
        assertContains(stderr, "Missing required parameter")
        assertContains(stderr, "NODE_REFERENCE")
        assertContains(stderr, "MODEL_TARGET")
        assertContains(stderr, "NODE_ID")
    }

    @Test
    fun `model get-node prints json node export for model target and node ID`() {
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
            """
            {
              "model": "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
              "concept": "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
              "id": "2110045694544566904"
            }
            """.trimIndent() + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `model get-node prints single-line json with --compact`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(JSON_FILE_REFERENCE)
        whenever(client.getNode(target)).thenReturn(ModelGetNodeResponse(node = jsonFileNode()))
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--compact", JSON_FILE_REFERENCE)
        }

        assertEquals(0, exitCode)
        assertEquals(
            """{"model":"r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)","concept":"jetbrains.mps.lang.structure.structure.ConceptDeclaration","id":"2110045694544566904"}""" +
                System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `model get-node passes the depth to the daemon and prints the truncation marker early`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(JSON_FILE_REFERENCE)
        whenever(client.getNode(target, false, 1)).thenReturn(
            ModelGetNodeResponse(
                node = jsonFileNode().copy(
                    truncatedAt = 1,
                    children = listOf(
                        MpsNodeJson(role = "implements", concept = "c.InterfaceConceptReference", reference = "$JSON_FILE_REFERENCE/1", childCount = 0),
                    ),
                ),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--depth", "1", JSON_FILE_REFERENCE)
        }

        assertEquals(0, exitCode)
        verify(client).getNode(target, false, 1)
        assertEquals("\"truncatedAt\": 1,", stdout.lines()[4].trim())
        assertContains(stdout, "\"childCount\": 0")
    }

    @Test
    fun `model get-node requests the summary with --summary`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(JSON_FILE_REFERENCE)
        whenever(client.getNodeSummary(target, true)).thenReturn(
            ModelGetNodeSummaryResponse(
                node = MpsNodeSummaryJson(
                    type = "root",
                    name = "JsonFile",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    reference = JSON_FILE_REFERENCE,
                    children = mapOf("implements" to 1, "linkDeclaration" to 1),
                ),
            ),
        )
        var exitCode = Int.MIN_VALUE

        val stdout = tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary", "--ancestry", JSON_FILE_REFERENCE)
        }

        assertEquals(0, exitCode)
        verify(client).getNodeSummary(target, true)
        verifyNoMoreInteractions(client)
        assertEquals(
            """
            {
              "type": "root",
              "name": "JsonFile",
              "concept": "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
              "reference": "$JSON_FILE_REFERENCE",
              "children": {
                "implements": 1,
                "linkDeclaration": 1
              }
            }
            """.trimIndent() + System.lineSeparator(),
            stdout,
        )
    }

    @Test
    fun `model get-node rejects --summary combined with --depth before contacting the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--summary", "--depth", "1", JSON_FILE_REFERENCE)
        }

        assertEquals(1, exitCode)
        assertContains(stderr, "--summary cannot be combined with --depth")
        verifyNoInteractions(client)
    }

    @Test
    fun `model get-node rejects a negative depth before contacting the daemon`() {
        val client = mock<DaemonClient>()
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--depth", "-1", JSON_FILE_REFERENCE)
        }

        assertEquals(1, exitCode)
        assertContains(stderr, "depth must not be negative")
        verifyNoInteractions(client)
    }

    @Test
    fun `model get-node points at --depth and --summary on stderr when an unbounded export is large`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(JSON_FILE_REFERENCE)
        whenever(client.getNode(target)).thenReturn(ModelGetNodeResponse(node = largeNode()))
        var exitCode = Int.MIN_VALUE
        var stdout = ""

        val stderr = tapSystemErr {
            stdout = tapSystemOut {
                exitCode = CommandLine(ModelGetNodeCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute(JSON_FILE_REFERENCE)
            }
        }

        assertEquals(0, exitCode)
        assertEquals(1, stderr.lines().count { it.isNotBlank() }, stderr)
        assertContains(stderr, "--depth")
        assertContains(stderr, "--summary")
        assertEquals(renderJson(largeNode(), pretty = true) + System.lineSeparator(), stdout)
    }

    @Test
    fun `model get-node stays quiet about size once a depth was given`() {
        val client = mock<DaemonClient>()
        val target = NodeTarget.NodeReference(JSON_FILE_REFERENCE)
        whenever(client.getNode(target, false, 3)).thenReturn(ModelGetNodeResponse(node = largeNode().copy(truncatedAt = 3)))
        var exitCode = Int.MIN_VALUE

        val stderr = tapSystemErr {
            tapSystemOut {
                exitCode = CommandLine(ModelGetNodeCommand(client))
                    .setExecutionExceptionHandler(PrintErrorAndExit)
                    .execute("--depth", "3", JSON_FILE_REFERENCE)
            }
        }

        assertEquals(0, exitCode)
        assertEquals("", stderr)
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
        assertContains(stdout, """"id": "2110045694544566904"""")
    }

    private fun jsonFileNode() = MpsNodeJson(
        model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
        concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
        id = "2110045694544566904",
    )

    // A node whose compact JSON is well above the 16 KB size hint threshold.
    private fun largeNode() = jsonFileNode().copy(
        children = (1..200).map {
            MpsNodeJson(
                role = "linkDeclaration",
                concept = "jetbrains.mps.lang.structure.structure.LinkDeclaration",
                id = it.toString(),
                properties = listOf(MpsNodePropertyJson("role", "child$it")),
            )
        },
    )

    @Test
    fun `model get-node passes the ancestry flag to the daemon`() {
        val client = mock<DaemonClient>()
        val nodeReference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
        val target = NodeTarget.NodeReference(nodeReference)
        whenever(client.getNode(target, true)).thenReturn(
            ModelGetNodeResponse(
                node = MpsNodeJson(
                    model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                    id = "2110045694544566904",
                ),
            ),
        )
        var exitCode = Int.MIN_VALUE

        tapSystemOut {
            exitCode = CommandLine(ModelGetNodeCommand(client))
                .setExecutionExceptionHandler(PrintErrorAndExit)
                .execute("--ancestry", nodeReference)
        }

        assertEquals(0, exitCode)
        verify(client).getNode(target, true)
    }

    private companion object {
        const val JSON_FILE_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"
    }
}
