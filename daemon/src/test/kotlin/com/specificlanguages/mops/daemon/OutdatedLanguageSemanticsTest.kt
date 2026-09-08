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
import kotlin.test.assertFalse
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Exercises language refusal evidence and recovery through daemon find/edit operations against real MPS projects. */
class OutdatedLanguageSemanticsTest {

    @Test
    fun `unsaved model changes are distinguished from persisted hash comparisons`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            assertEquals(MakeOutcome.SUCCESS, access.extra { makeModules(listOf(SANDBOX_MODULE)) }.outcome)
            val model = project.modelAccess.computeReadAction {
                project.repository.modules.flatMap { it.models }.single { it.name.value == "$JSON_LANGUAGE.structure" }
                    as org.jetbrains.mps.openapi.model.EditableSModel
            }
            project.modelAccess.runWriteAction { model.isChanged = true }
            try {
                val failure = assertFailsWith<MpsRequestException> {
                    access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
                }
                val message = assertNotNull(failure.message)
                assertContains(message, "model has unsaved changes")
                assertFalse(message.contains("hash differs"), message)
                assertFalse(message.contains("Current hash:"), message)
                assertFalse(message.contains("Recorded hash:"), message)
            } finally {
                project.modelAccess.runWriteAction { model.isChanged = false }
            }
        }
    }

    @Test
    fun `unreadable model source reports an unavailable current hash`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, path ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())
            assertEquals(MakeOutcome.SUCCESS, access.extra { makeModules(listOf(SANDBOX_MODULE)) }.outcome)
            val source = project.modelAccess.computeReadAction {
                project.repository.modules.flatMap { it.models }.single { it.name.value == "$JSON_LANGUAGE.structure" }.source
                    as jetbrains.mps.extapi.persistence.FileDataSource
            }
            val original = source.file
            val missing = path.resolve("unavailable-source.mps")
            val io = project.getComponent(jetbrains.mps.vfs.VFSManager::class.java)
                .getFileSystem(jetbrains.mps.vfs.VFSManager.JAVA_IO_FILE_FS)
            project.modelAccess.runWriteAction { source.file = io.getFile(missing.toString()) }
            try {
                val failure = assertFailsWith<MpsRequestException> {
                    access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
                }
                val message = assertNotNull(failure.message)
                assertContains(message, "current source hash is unavailable")
                assertContains(message, "Current hash: unavailable")
                assertFalse(message.contains("Recorded hash:"), message)
                assertContains(message, missing.toString())
                assertFalse(message.contains("hash differs"), message)
            } finally {
                project.modelAccess.runWriteAction { source.file = original }
            }
        }
    }

    @Test
    fun `missing generation evidence is explained without asserting older compiled sources`() {
        SharedMpsEnvironment.withProjectCopy { access, path ->
            assertEquals(MakeOutcome.SUCCESS, access.extra { makeModules(listOf(SANDBOX_MODULE)) }.outcome)
            val record = path.resolve("languages/$JSON_LANGUAGE/source_gen.caches/com/specificlanguages/json/structure/generated")
            Files.delete(record)
            val failure = assertFailsWith<MpsRequestException> {
                access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            }
            val message = assertNotNull(failure.message)
            assertContains(message, "generation record is missing")
            assertContains(message, "Generation record: $record")
            assertContains(message, "Recorded hash: unavailable")
            assertFalse(message.contains("older sources"), message)
        }
    }

    @Test
    fun `model detail limit is global across languages with accurate omitted counts`() {
        SharedMpsEnvironment.withProjectCopy(prepare = ::addSecondLanguage) { access, path ->
            val built = access.extra { makeModules(listOf(SANDBOX_MODULE, "example.more")) }
            assertEquals(MakeOutcome.SUCCESS, built.outcome, built.toString())
            val records = listOf("behavior", "constraints", "editor", "structure", "textGen", "typesystem").map {
                path.resolve("languages/$JSON_LANGUAGE/source_gen.caches/com/specificlanguages/json/$it/generated")
            } + listOf(path.resolve("languages/example.more/source_gen.caches/example/more/structure/generated"))
            val originals = records.associateWith { it.readText() }
            for (count in listOf(2, 5, 7)) {
                originals.forEach { (record, text) -> record.writeText(text) }
                records.take(count).forEach { record -> Files.delete(record) }
                val failure = assertFailsWith<MpsRequestException> {
                    access.read { findInstances("UnknownConcept", exact = false, limit = 0) }
                }
                val message = assertNotNull(failure.message)
                assertEquals(minOf(count, 5), message.lineSequence().count { it.trimStart().startsWith("Model:") }, message)
                if (count > 5) {
                    assertContains(message, "2 additional models require generation")
                    assertContains(message, "example.more")
                } else {
                    assertFalse(message.contains("additional models"), message)
                }
            }
        }
    }

    @Test
    fun `a nonempty output directory does not prove that the runtime loaded`() {
        SharedMpsEnvironment.withProjectCopy(prepare = { path ->
            val output = path.resolve("languages/$JSON_LANGUAGE/classes_gen")
            Files.createDirectories(output)
            output.resolve("unrelated-resource.txt").writeText("not a language runtime")
        }) { access, path ->
            val failure = assertFailsWith<MpsRequestException> {
                access.read { findInstances("JsonFile", exact = false, limit = 0) }
            }
            val message = assertNotNull(failure.message)
            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, failure.code)
            assertContains(message, "runtime is not loaded")
            assertContains(message, "RUNTIME_LOAD_FAILED")
            assertContains(message, path.resolve("languages/$JSON_LANGUAGE/classes_gen").toString())
            assertFalse(message.contains("not built"), message)
        }
    }

    @Test
    fun `notices a generation record repaired on disk without rebuilding or restarting`() {
        SharedMpsEnvironment.withProjectCopy { access, path ->
            assertEquals(MakeOutcome.SUCCESS, access.extra { makeModules(listOf(SANDBOX_MODULE)) }.outcome)
            val record = Files.walk(path).use { paths ->
                paths.filter { it.fileName.toString() == "generated" && it.parent.fileName.toString() == "structure" }
                    .findFirst().orElseThrow()
            }
            val original = record.readText()
            val expectedCurrentHash = Regex("modelHash=\"([^\"]+)\"").find(original)!!.groupValues[1]
            record.writeText(original.replace(Regex("modelHash=\"[^\"]+\""), "modelHash=\"different-recorded-hash\""))
            val rejected = assertFailsWith<MpsRequestException> {
                access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            }
            assertEquals(MpsErrorCode.LANGUAGE_NOT_LOADED, rejected.code)
            assertContains(assertNotNull(rejected.message), "different-recorded-hash")
            assertContains(rejected.message, "Current hash: $expectedCurrentHash")
            assertContains(rejected.message, "Generation record: $record")
            assertContains(rejected.message, path.resolve("languages/$JSON_LANGUAGE/models/$JSON_LANGUAGE.structure.mps").toString())
            record.writeText(original)
            val recovered = access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            assertTrue(recovered.nodes.isNotEmpty(), "the existing json file is found after the record is restored")

            record.writeText(original.replace(Regex("modelHash=\"[^\"]+\""), ""))
            val missingHash = assertFailsWith<MpsRequestException> {
                access.read { findInstances(JSON_FILE_CONCEPT, exact = false, limit = 0) }
            }
            assertContains(assertNotNull(missingHash.message), "generation record has no readable recorded hash")
        }
    }

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

            // Delete the concept's declaration from the sources. The compiled runtime still
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
            assertContains(findMessage, "current source hash differs from the recorded generation hash")
            assertContains(findMessage, "Model: com.specificlanguages.json.structure")
            assertContains(findMessage, "Source:")
            assertContains(findMessage, "Current hash:")
            assertContains(findMessage, "Recorded hash:")
            assertContains(findMessage, "Generation record:")

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
            // — the edit used the concept identity that resolution now yields.
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
        fun addSecondLanguage(path: java.nio.file.Path) {
            val source = path.resolve("languages/$JSON_LANGUAGE")
            val files = Files.walk(source).use { paths -> paths.filter(Files::isRegularFile).toList() }
            val ids = files.filter { it.toString().endsWith(".mps") }.map {
                Regex("<model ref=\"r:([^ (]+)").find(it.readText())!!.groupValues[1]
            } + "f3f42ddf-d692-4c29-90fb-7360196f01ab"
            val replacements = ids.associateWith { java.util.UUID.randomUUID().toString() }
            for (file in files) {
                val destination = path.resolve("languages/example.more")
                    .resolve(source.relativize(file).toString().replace(JSON_LANGUAGE, "example.more"))
                Files.createDirectories(destination.parent)
                var text = file.readText().replace(JSON_LANGUAGE, "example.more")
                replacements.forEach { (old, new) -> text = text.replace(old, new) }
                destination.writeText(text)
            }
            val modules = path.resolve(".mps/modules.xml")
            modules.writeText(modules.readText().replace("</projectModules>",
                "<modulePath path=\"\$PROJECT_DIR\$/languages/example.more/example.more.mpl\" /></projectModules>"))
        }

        const val SANDBOX_MODULE = "json.sandbox"
        const val JSON_LANGUAGE = "com.specificlanguages.json"
        const val JSON_FILE_CONCEPT = "com.specificlanguages.json.structure.JsonFile"
        const val STALE_WIDGET_CONCEPT = "com.specificlanguages.json.structure.StaleWidget"
        const val JSON_STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val SANDBOX_MODEL = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)"
        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
    }
}
