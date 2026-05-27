package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.DefaultDaemonClient
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class DefaultDaemonClientTest {
    @Test
    fun `get node sends model target and node id request`() {
        val response = ModelGetNodeResponse(
            node = mapOf(
                "model" to "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)",
                "concept" to "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                "id" to "2110045694544566904",
            ),
        )
        val daemon = startPrerecordedDaemon(response)

        val actual = DefaultDaemonClient(daemon.port, "secret").getNode(
            modelTarget = "/project/models/main.mps",
            nodeId = "2110045694544566904",
            nodeReference = null,
        )

        daemon.join(5_000)
        assertEquals(response, actual)
        assertContains(daemon.requestsReceived.single(), "\"type\":\"model-get-node\"")
        assertContains(daemon.requestsReceived.single(), "\"modelTarget\":\"/project/models/main.mps\"")
        assertContains(daemon.requestsReceived.single(), "\"nodeId\":\"2110045694544566904\"")
    }
}
