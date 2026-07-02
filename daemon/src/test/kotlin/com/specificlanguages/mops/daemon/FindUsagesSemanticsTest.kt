package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.NodeTarget
import kotlin.test.Test
import kotlin.test.assertTrue

class FindUsagesSemanticsTest {

    @Test
    fun `finds reference usages of a node reference`() {
        val payload = assertOk(
            SharedMpsEnvironment.sharedMpsAccess.read {
                findUsages(NodeTarget.NodeReference(IJSON_VALUE_REFERENCE), limit = DEFAULT_LIMIT)
            },
        )

        assertTrue(payload.usages.isNotEmpty())
        assertTrue(payload.usages.any { it.role == "intfc" }, "expected an implements usage, got: ${payload.usages}")
        assertTrue(
            payload.usages.all { it.owner.reference.startsWith("r:") && it.owner.concept.isNotBlank() },
            "every usage owner should be a real node, got: ${payload.usages}",
        )
    }

    @Test
    fun `finds reference usages of a model target and node id`() {
        val payload = assertOk(
            SharedMpsEnvironment.sharedMpsAccess.read {
                findUsages(
                    NodeTarget.InModel(modelTarget = "com.specificlanguages.json.structure", nodeId = IJSON_VALUE_NODE_ID),
                    limit = DEFAULT_LIMIT,
                )
            },
        )

        assertTrue(payload.usages.isNotEmpty())
        assertTrue(payload.usages.any { it.role == "intfc" }, "expected an implements usage, got: ${payload.usages}")
    }

    private companion object {
        const val IJSON_VALUE_NODE_ID = "2110045694544566909"
        const val IJSON_VALUE_REFERENCE =
            "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)/$IJSON_VALUE_NODE_ID"
        const val DEFAULT_LIMIT = 100
    }
}
