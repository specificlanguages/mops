package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class InstancesDiagnosticProtocolTest {
    @Test
    fun `diagnostic query and incomplete result round trip`() {
        val request = DiagnoseInstancesRequest(
            "secret", "Concept", true, listOf("/"), listOf(NodeFilter.Named("foo")), 0, "node-ref",
        )
        assertEquals(request, ProtocolJson.decodeRequest(ProtocolJson.encodeRequest(request)))
        val response = InstancesDiagnosticResponse(
            InstanceConceptJson("Concept", "id", true), true, listOf("/"), "facade", "2025.1.2",
            listOf("Participant"), listOf(InstanceModelDiagnosticJson(
                "model-ref", "Model", "/file", "Source", listOf("/file"), false, true, false,
                null, 2, 3, 4, listOf(InstanceDifferenceJson("model-lookup", 1, listOf("node"), 0, emptyList())),
                emptyList(),
            )), null, null, null, 0, ExpectedInstanceJson("node-ref", false), listOf("facade failed"), false,
        )
        assertEquals(response, ProtocolJson.decodeResponse(ProtocolJson.encodeResponse(response)))
    }
}
