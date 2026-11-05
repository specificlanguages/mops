package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.NamePatternTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FindRootByNameSemanticsTest {

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
    fun `in slash searches library models beyond editable project sources`() {
        val editable = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Concept", limit = 0)
        }
        val repository = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Concept", limit = 0, scope = resolveScope(listOf("/")))
        }

        val editableReferences = editable.nodes.map { it.reference }.toSet()
        val repositoryReferences = repository.nodes.map { it.reference }.toSet()

        assertTrue(
            repositoryReferences.size > editableReferences.size,
            "searching the whole repository should reach library matches the editable search excludes",
        )
        assertTrue(
            editableReferences.all { it in repositoryReferences },
            "editable results must remain a subset of the repository results",
        )
    }

    @Test
    fun `scopes a root-by-name search to a module`() {
        val inModule = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Json", limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE)))
        }
        val inRepository = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Json", limit = 0, scope = resolveScope(listOf("/")))
        }

        val moduleReferences = inModule.nodes.map { it.reference }.toSet()
        val repositoryReferences = inRepository.nodes.map { it.reference }.toSet()

        assertTrue(moduleReferences.isNotEmpty(), "the language module should hold Json-named roots")
        assertTrue(
            moduleReferences.all { it in repositoryReferences },
            "module results must remain a subset of the repository results",
        )
    }

    @Test
    fun `scopes a root-by-name search to a model`() {
        val inModel = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("Json", limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)))
        }

        assertTrue(inModel.nodes.isNotEmpty(), "the structure model should hold Json-named roots")
        assertTrue(
            inModel.nodes.all { it.reference.contains("($STRUCTURE_MODEL)") },
            "every model-scoped result must belong to the structure model: ${inModel.nodes}",
        )
    }

    @Test
    fun `an empty result names the pattern and the model it searched`() {
        val payload = SharedMpsEnvironment.sharedMpsAccess.read {
            findByName("NoSuchRoot", limit = 0, scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL)))
        }

        assertEquals(
            FindByNameResponse(
                target = NamePatternTargetJson("NoSuchRoot"),
                scope = SearchScopeJson(
                    kind = SearchScopeKind.MODEL,
                    reference = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6($STRUCTURE_MODEL)",
                    name = STRUCTURE_MODEL,
                    models = 1,
                ),
                limit = 0,
                truncated = false,
                nodes = emptyList(),
            ),
            payload,
        )
    }

    @Test
    fun `rejects a subtree scope pointing at the named-descendant search`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read {
                findByName(
                    "Json",
                    limit = 0,
                    scope = resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL, "JsonObject")),
                )
            }
        }

        assertEquals(MpsErrorCode.UNSUPPORTED_TARGET, exception.code)
        assertContains(exception.message, "find root-by-name searches root nodes only")
        assertContains(exception.message, "find instances <concept> --named <pattern>")
        assertContains(exception.message, "mops explain scope")
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
        const val LANGUAGE_MODULE = "com.specificlanguages.json"
        const val STRUCTURE_MODEL = "com.specificlanguages.json.structure"
    }
}
