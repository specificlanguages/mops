package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reproduces the field-test failure behind `.scratch/stale-language-runtimes`: name-based resolution through a compiled
 * language runtime that is out of date with the structure sources on disk. Building the language, then changing one of
 * its models without regenerating, leaves the runtime "built but stale" — resolution must refuse rather than answer
 * from the stale runtime, and must recover after a rebuild.
 */
class OutdatedLanguageSemanticsTest {

    @Test
    fun `refuses find and edit through a stale language runtime and recovers after rebuild`() {
        SharedMpsEnvironment.withProjectCopy { access, _ ->
            val made = access.extra { makeModules(listOf(SANDBOX_MODULE)) }
            assertEquals(MakeOutcome.SUCCESS, made.outcome, "building the json language must succeed: ${made.messages}")

            // Baseline: while the runtime matches its sources, resolution works.
            val baseline = access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            assertTrue(baseline.nodes.isNotEmpty(), "the sandbox should hold JsonFile instances once the language is built")

            // Make the runtime stale: change a model of the json language and save it, without regenerating. The
            // structure model now differs from what the compiled runtime was generated from.
            access.write {
                modelEdit(
                    EditBatch(
                        listOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(JSON_STRUCTURE_MODEL),
                                concept = CONCEPT_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "StaleMarkerConcept")),
                            ),
                        ),
                    ),
                )
            }

            // find instances by FQN is refused, not answered from the stale runtime.
            val findFailure = assertFailsWith<MpsRequestException> {
                access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, findFailure.code)
            val findMessage = assertNotNull(findFailure.message)
            assertContains(findMessage, JSON_LANGUAGE)
            assertContains(findMessage, "built from older sources")

            // Editing a model that uses the stale language is refused before any node is written.
            val editFailure = assertFailsWith<MpsRequestException> {
                access.write {
                    modelEdit(
                        EditBatch(
                            listOf(
                                EditOperation.AddRoot(
                                    model = ModelDestination(SANDBOX_MODEL),
                                    concept = JSON_FILE_CONCEPT,
                                ),
                            ),
                        ),
                    )
                }
            }
            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, editFailure.code)
            assertContains(assertNotNull(editFailure.message), JSON_LANGUAGE)

            // Rebuild: the runtime matches its sources again.
            val remade = access.extra { makeModules(listOf(SANDBOX_MODULE)) }
            assertEquals(MakeOutcome.SUCCESS, remade.outcome, "rebuilding the json language must succeed: ${remade.messages}")

            val recovered = access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            assertTrue(recovered.nodes.isNotEmpty(), "resolution works again once the language is rebuilt")

            val edit = access.write {
                modelEdit(
                    EditBatch(
                        listOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(SANDBOX_MODEL),
                                concept = JSON_FILE_CONCEPT,
                                alias = "\$ok",
                            ),
                        ),
                    ),
                )
            }
            assertTrue(edit.created.containsKey("ok"), "the edit succeeds once the language is rebuilt: $edit")
        }
    }

    private companion object {
        const val SANDBOX_MODULE = "json.sandbox"
        const val JSON_LANGUAGE = "com.specificlanguages.json"
        const val JSON_FILE_CONCEPT = "com.specificlanguages.json.structure.JsonFile"
        const val JSON_STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val SANDBOX_MODEL = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)"
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
