package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.daemon.core.ResolvedScope
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Resolution of the `in`-clause segment list into a [ResolvedScope], exercised on its own so scope resolution is
 * verified independently of the searches that run over it.
 */
class ScopeResolutionTest {

    @Test
    fun `resolves an absent clause to editable project sources`() {
        assertEquals(
            ResolvedScope.EditableProjectSources,
            SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(null) },
        )
        assertEquals(
            ResolvedScope.EditableProjectSources,
            SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(emptyList()) },
        )
    }

    @Test
    fun `resolves slash to the repository`() {
        assertEquals(
            ResolvedScope.Repository,
            SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(listOf("/")) },
        )
    }

    @Test
    fun `resolves a module by name or reference to the same module scope`() {
        val byName = SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(listOf(LANGUAGE_MODULE)) }
        val byReference = SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(listOf(LANGUAGE_MODULE_REFERENCE)) }

        assertEquals(ResolvedScope.Module(LANGUAGE_MODULE_REFERENCE), byName)
        assertEquals(ResolvedScope.Module(LANGUAGE_MODULE_REFERENCE), byReference)
    }

    @Test
    fun `resolves a module and model to a model scope`() {
        val byPath = SharedMpsEnvironment.sharedMpsAccess.read {
            resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL))
        }
        val byReference = SharedMpsEnvironment.sharedMpsAccess.read {
            resolveScope(listOf(STRUCTURE_MODEL_REFERENCE))
        }

        assertEquals(ResolvedScope.Model(STRUCTURE_MODEL_REFERENCE), byPath)
        assertEquals(ResolvedScope.Model(STRUCTURE_MODEL_REFERENCE), byReference)
    }

    @Test
    fun `resolves a root node by path or reference to a subtree scope`() {
        val byPath = SharedMpsEnvironment.sharedMpsAccess.read {
            resolveScope(listOf(LANGUAGE_MODULE, STRUCTURE_MODEL, "JsonFile"))
        }
        val byReference = SharedMpsEnvironment.sharedMpsAccess.read {
            resolveScope(listOf(JSON_FILE_NODE_REFERENCE))
        }

        assertEquals(ResolvedScope.Subtree(JSON_FILE_NODE_REFERENCE), byPath)
        assertEquals(ResolvedScope.Subtree(JSON_FILE_NODE_REFERENCE), byReference)
    }

    @Test
    fun `reports an unresolved scope segment as not found referencing the scope topic`() {
        val exception = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.sharedMpsAccess.read { resolveScope(listOf("no.such.module")) }
        }

        assertEquals(MpsErrorCode.TARGET_NOT_FOUND, exception.code)
        assertContains(exception.message, "scope not found: no.such.module")
        assertContains(exception.message, "mops explain scope")
    }

    @Test
    fun `reports an ambiguous scope segment with candidates referencing the scope topic`() {
        val duplicateReference = "11111111-2222-4333-8444-555555555555(com.specificlanguages.json.build)"

        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val originalDescriptor = project.resolve(
                    "solutions/com.specificlanguages.json.build/com.specificlanguages.json.build.msd",
                )
                val duplicateDirectory = project.resolve("solutions/duplicate-json-build").createDirectories()
                duplicateDirectory.resolve("duplicate-json-build.msd").writeText(
                    originalDescriptor.readText().replace(
                        "84f0ad52-c7ca-45dd-99c5-9605c96bf808",
                        "11111111-2222-4333-8444-555555555555",
                    ),
                )
                val modulesXml = project.resolve(".mps/modules.xml")
                modulesXml.writeText(
                    modulesXml.readText().replace(
                        "    </projectModules>",
                        "      <modulePath path=\"\$PROJECT_DIR\$/solutions/duplicate-json-build/duplicate-json-build.msd\" folder=\"\" />\n" +
                            "    </projectModules>",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read { resolveScope(listOf("com.specificlanguages.json.build")) }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous module target com.specificlanguages.json.build")
            assertContains(exception.message, duplicateReference)
            assertContains(exception.message, "mops explain scope")
        }
    }

    private companion object {
        const val LANGUAGE_MODULE = "com.specificlanguages.json"
        const val LANGUAGE_MODULE_REFERENCE = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"
        const val STRUCTURE_MODEL = "com.specificlanguages.json.structure"
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val JSON_FILE_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/2110045694544566904"
    }
}
