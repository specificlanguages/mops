package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DefaultDaemonClient
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson
import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DefaultDaemonClientTest {
    @Test
    fun `get node sends model target and node id request`() {
        val response = ModelGetNodeResponse(
            node = MpsNodeJson(
                model = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                id = "2110045694544566904",
            ),
        )
        val daemon = startPrerecordedDaemon(response)

        val actual = DefaultDaemonClient(daemon.port, "secret").getNode(
            NodeTarget.InModel(
                modelTarget = "/project/models/main.mps",
                nodeId = "2110045694544566904",
            ),
        )

        daemon.join(5_000)
        assertEquals(response, actual)
        assertContains(daemon.requestsReceived.single(), "\"type\":\"model-get-node\"")
        assertContains(daemon.requestsReceived.single(), "\"target\"")
        assertContains(daemon.requestsReceived.single(), "\"modelTarget\":\"/project/models/main.mps\"")
        assertContains(daemon.requestsReceived.single(), "\"nodeId\":\"2110045694544566904\"")
    }

    @Test
    fun `list sends target and depth request`() {
        val response = MpsListResponse(
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
        val daemon = startPrerecordedDaemon(response)

        val actual = DefaultDaemonClient(daemon.port, "secret").list(
            target = listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"),
            depth = 1,
        )

        daemon.join(5_000)
        assertEquals(response, actual)
        assertContains(daemon.requestsReceived.single(), "\"type\":\"list\"")
        assertContains(
            daemon.requestsReceived.single(),
            "\"target\":[\"com.specificlanguages.json\",\"com.specificlanguages.json.structure\"]",
        )
        assertContains(daemon.requestsReceived.single(), "\"depth\":1")
    }

    @Test
    fun `find usages sends target and limit request`() {
        val response = FindUsagesResponse(
            limit = 100,
            truncated = false,
            usages = listOf(
                MpsNodeUsageJson(
                    role = "concept",
                    owner = MpsNodeSummaryJson(
                        type = "node",
                        name = "JsonObject",
                        concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                        reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566905",
                    ),
                ),
            ),
        )
        val daemon = startPrerecordedDaemon(response)
        val nodeReference =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/2110045694544566904"

        val actual = DefaultDaemonClient(daemon.port, "secret").findUsages(
            target = NodeTarget.NodeReference(nodeReference),
            limit = 100,
        )

        daemon.join(5_000)
        assertEquals(response, actual)
        assertContains(daemon.requestsReceived.single(), "\"type\":\"find-usages\"")
        assertContains(daemon.requestsReceived.single(), "\"target\"")
        assertContains(daemon.requestsReceived.single(), "\"nodeReference\":\"$nodeReference\"")
        assertContains(daemon.requestsReceived.single(), "\"limit\":100")
    }
}
