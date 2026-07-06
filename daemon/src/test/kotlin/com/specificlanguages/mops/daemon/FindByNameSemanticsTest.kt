package com.specificlanguages.mops.daemon

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FindByNameSemanticsTest {

    @Test
    fun `finds root nodes whose name matches a prefix`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Json", limit = DEFAULT_LIMIT)
        }

        assertTrue(payload.nodes.isNotEmpty())
        assertTrue(payload.nodes.all { it.type == "root" }, "matches are root nodes: ${payload.nodes}")
        assertTrue(
            payload.nodes.all { it.reference.startsWith("r:") },
            "every node reference should be serialized: ${payload.nodes}",
        )
        val names = payload.nodes.mapNotNull { it.name }.toSet()
        assertContains(names, "JsonObject")
        assertContains(names, "JsonArray")
    }

    @Test
    fun `matches camel-hump abbreviations`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("JN", limit = DEFAULT_LIMIT)
        }

        val names = payload.nodes.mapNotNull { it.name }.toSet()
        assertContains(names, "JsonNumber")
        assertContains(names, "JsonNull")
        assertTrue(
            names.none { it == "JsonArray" },
            "JsonArray has no N hump and must not match JN: $names",
        )
    }

    @Test
    fun `matches a fragment anywhere within the name`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Object", limit = DEFAULT_LIMIT)
        }

        val names = payload.nodes.mapNotNull { it.name }.toSet()
        assertContains(names, "JsonObject")
    }

    @Test
    fun `all searches library models beyond editable project sources`() {
        val editable = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Concept", limit = 0, all = false)
        }
        val all = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Concept", limit = 0, all = true)
        }

        val editableReferences = editable.nodes.map { it.reference }.toSet()
        val allReferences = all.nodes.map { it.reference }.toSet()

        assertTrue(
            allReferences.size > editableReferences.size,
            "searching all models should reach library matches the editable search excludes",
        )
        assertTrue(
            editableReferences.all { it in allReferences },
            "editable results must remain a subset of the all-models results",
        )
    }

    @Test
    fun `truncates results with a low limit`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Json", limit = 1)
        }

        assertTrue(payload.nodes.size == 1)
        assertTrue(payload.truncated, "more Json-named roots exist than the limit: $payload")
    }

    private companion object {
        const val DEFAULT_LIMIT = 100
    }
}
