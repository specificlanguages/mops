package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
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
 * Reproduces the field-test failure behind `.scratch/stale-language-runtimes`: a compiled language runtime that still
 * registers a concept the structure sources on disk no longer declare. The daemon must refuse name-based resolution
 * through such a stale runtime — never silently serve the deleted concept's identity — and recover once the language is
 * rebuilt.
 *
 * The reproduction deletes the very concept it then resolves, so the harm is the specific one from the PRD forensics:
 * the compiled runtime knows `StaleWidget` while the sources do not, and without the guard `find instances` would
 * return an empty result (no error) and an edit would write the stale concept id into a model header.
 */
class OutdatedLanguageSemanticsTest {

    @Test
    fun `refuses find and edit through a stale language runtime and recovers after rebuild`() {
        SharedMpsEnvironment.withProjectCopy { access, _ ->
            fun make() = access.extra { makeModules(listOf(SANDBOX_MODULE)) }

            assertEquals(MakeOutcome.SUCCESS, make().outcome, "building the json language must succeed")

            // Add a concept to the json language, then build it into the runtime.
            val added = access.write {
                modelEdit(
                    EditBatch(
                        listOf(
                            EditOperation.AddRoot(
                                model = ModelDestination(JSON_STRUCTURE_MODEL),
                                concept = CONCEPT_DECLARATION,
                                properties = listOf(MpsNodePropertyJson(name = "name", value = "StaleWidget")),
                                alias = "\$widget",
                            ),
                        ),
                    ),
                )
            }
            val widgetDeclaration = assertNotNull(added.created["widget"], "the concept declaration must be created")
            assertEquals(MakeOutcome.SUCCESS, make().outcome, "rebuilding with the new concept must succeed")

            // Once built, the concept resolves by name — a valid resolution with no instances yet, not a refusal.
            val whenBuilt = access.read { findInstances(STALE_WIDGET_CONCEPT, exact = false, limit = 0) }
            assertTrue(whenBuilt.nodes.isEmpty(), "the freshly built concept has no instances yet: ${whenBuilt.nodes}")

            // Induce the exact PRD harm: delete the concept's declaration from the sources. The compiled runtime still
            // registers StaleWidget, so its structure model is now stale relative to disk.
            access.write {
                modelEdit(EditBatch(listOf(EditOperation.Delete(EditTarget.NodeReference(widgetDeclaration)))))
            }

            // find instances by FQN is refused as stale, not answered with a silent empty result from the runtime.
            val findFailure = assertFailsWith<MpsRequestException> {
                access.read { findInstances(STALE_WIDGET_CONCEPT, exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, findFailure.code)
            val findMessage = assertNotNull(findFailure.message)
            assertContains(findMessage, JSON_LANGUAGE)
            assertContains(findMessage, "built from older sources")

            // Editing the sandbox, which uses the now-stale json language, is refused before any node is written — so no
            // stale concept id can reach the model header.
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

            // Rebuild: regeneration drops StaleWidget from the runtime, so the stale registration is gone.
            assertEquals(MakeOutcome.SUCCESS, make().outcome, "rebuilding after the deletion must succeed")

            val afterRebuild = assertFailsWith<MpsRequestException> {
                access.read { findInstances(STALE_WIDGET_CONCEPT, exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, afterRebuild.code, "the deleted concept is truly gone, not stale")

            // A still-valid concept edits fine again, and the created instance is recognised by a fresh name resolution
            // — the edit used the concept identity that resolution now yields (acceptance criterion 3).
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
            val createdInstance = assertNotNull(edit.created["ok"], "the edit succeeds once the language is up to date")
            val recovered = access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            assertTrue(
                recovered.nodes.any { it.reference == createdInstance },
                "a fresh find must recognise the instance the edit created: $createdInstance not in ${recovered.nodes.map { it.reference }}",
            )
        }
    }

    private companion object {
        const val SANDBOX_MODULE = "json.sandbox"
        const val JSON_LANGUAGE = "com.specificlanguages.json"
        const val JSON_FILE_CONCEPT = "com.specificlanguages.json.structure.JsonFile"
        const val STALE_WIDGET_CONCEPT = "com.specificlanguages.json.structure.StaleWidget"
        const val JSON_STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val SANDBOX_MODEL = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)"
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
