package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.EditTarget
import com.specificlanguages.mops.protocol.InlineChild
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Editing a model must leave it declaring the dependencies its new content relies on: a concept's language among the
 * model's used languages, a reference target's model among its imported models. Mirrors the MPS editor, which adds
 * those imports as a side effect of creating a node or reference; the headless edit path must do the same or a batch
 * can leave the model using an undeclared language or referencing an unimported model.
 */
class AutoImportEditSemanticsTest {

    @Test
    fun `instantiating a concept from an unused language adds that language to the model`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            assertFalse(
                usesLanguage(structureModel(projectPath), BASE_LANGUAGE),
                "the structure model does not use baseLanguage before the edit",
            )

            // ClassConcept is not a legal root of a structure model; advisory enforcement saves the edit despite the
            // can-be-root violation, so the import behavior can be observed in isolation.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CLASS_CONCEPT,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Helper")),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            assertTrue(
                usesLanguage(structureModel(projectPath), BASE_LANGUAGE),
                "instantiating a baseLanguage concept must add baseLanguage to the model's used languages",
            )
        }
    }

    @Test
    fun `a concept language deep inside a created subtree is added, not only the root's`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            // The created root is a ConceptDeclaration (jetbrains.mps.lang.structure, already used); only the nested
            // ClassConcept pulls in baseLanguage. Scanning the whole created subtree — not just its root — is what
            // surfaces it. Advisory saves despite the containment violation of the misplaced child.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Outer")),
                            children = listOf(InlineChild.Fresh(role = "propertyDeclaration", concept = CLASS_CONCEPT)),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            assertTrue(
                usesLanguage(structureModel(projectPath), BASE_LANGUAGE),
                "a language used only by a descendant of the created node must still be added",
            )
        }
    }

    @Test
    fun `setting a reference to a node in an unimported model adds that model import`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            assertFalse(
                importsModel(structureModel(projectPath), SANDBOX_MODEL_NAME),
                "the structure model does not import the sandbox model before the edit",
            )

            // Point an existing reference at a node in the sandbox model, which the structure model does not import.
            // Advisory saves despite the reference-scope violation, isolating the model-import behavior.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.SetReference(
                            target = EditTarget.NodeReference("$STRUCTURE_MODEL/$JSON_STRING_VALUE_ID"),
                            role = "dataType",
                            to = EditTarget.NodeReference("$SANDBOX_MODEL/$SANDBOX_ROOT_ID"),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            assertTrue(
                importsModel(structureModel(projectPath), SANDBOX_MODEL_NAME),
                "referencing a node in the sandbox model must add it to the structure model's imports",
            )
        }
    }

    @Test
    fun `copying a subtree into another model adds the language the copy uses`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            assertFalse(
                usesLanguage(buildModel(projectPath), BASE_LANGUAGE),
                "the build model does not use baseLanguage before the edit",
            )

            // Create a baseLanguage node in the structure model, then copy it into the build model. baseLanguage can
            // reach the build model only through the copy's subtree, so this exercises the copy path specifically.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CLASS_CONCEPT,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "Source")),
                            alias = "\$src",
                        ),
                        EditOperation.CopyAsRoot(
                            model = ModelDestination(BUILD_MODEL),
                            source = EditTarget.Alias("\$src"),
                        ),
                    ),
                    constraints = ConstraintEnforcement.ADVISORY,
                )
            }

            assertTrue(
                usesLanguage(buildModel(projectPath), BASE_LANGUAGE),
                "a subtree copied into the build model must add the language its nodes use",
            )
        }
    }

    @Test
    fun `an edit using only already-declared dependencies adds no import`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val languagesBefore = languagesBlock(structureModel(projectPath))
            val importsBefore = importsBlock(structureModel(projectPath))

            // A ConceptDeclaration is jetbrains.mps.lang.structure, which the model already uses; nothing new to import.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddRoot(
                            model = ModelDestination(STRUCTURE_MODEL),
                            concept = CONCEPT_DECLARATION,
                            properties = listOf(MpsNodePropertyJson(name = "name", value = "JsonComment")),
                        ),
                    ),
                )
            }

            assertEquals(
                languagesBefore,
                languagesBlock(structureModel(projectPath)),
                "an edit using only already-used languages must not touch the used-languages block",
            )
            assertEquals(
                importsBefore,
                importsBlock(structureModel(projectPath)),
                "an edit adding no cross-model reference must not touch the model-imports block",
            )
        }
    }

    @Test
    fun `a blocked edit adds no import`() {
        SharedMpsEnvironment.withProjectCopy { mpsAccess, projectPath ->
            val before = structureModel(projectPath).readText()

            // ClassConcept under propertyDeclaration is a containment violation; under best-effort the batch blocks and
            // reverts. The would-be baseLanguage import must revert with it — imports are added only on a saving path.
            mpsAccess.write {
                modelEdit(
                    batchOf(
                        EditOperation.AddChild(
                            target = EditTarget.NodeReference("$STRUCTURE_MODEL/$JSON_ARRAY_ID"),
                            role = "propertyDeclaration",
                            concept = CLASS_CONCEPT,
                        ),
                    ),
                )
            }

            assertEquals(before, structureModel(projectPath).readText(), "a blocked edit leaves the model untouched")
        }
    }

    private fun batchOf(vararg operations: EditOperation): EditBatch = EditBatch(operations.toList())

    private fun structureModel(projectPath: Path): Path = projectPath.resolve(STRUCTURE_MODEL_PATH)

    private fun buildModel(projectPath: Path): Path = projectPath.resolve(BUILD_MODEL_PATH)

    // The `<languages>` header block, listing the model's used languages and devkits.
    private fun languagesBlock(model: Path): String = block(model.readText(), "languages")

    // The `<imports>` header block, listing the models this model imports.
    private fun importsBlock(model: Path): String = block(model.readText(), "imports")

    private fun block(text: String, tag: String): String {
        val open = text.indexOf("<$tag>")
        val selfClosed = text.indexOf("<$tag />")
        if (open < 0) {
            // An empty block persists as `<imports />`; treat its content as empty.
            return if (selfClosed >= 0) "" else error("no <$tag> block in model")
        }
        val close = text.indexOf("</$tag>", open)
        return text.substring(open, close)
    }

    // Whether the model's used-languages block declares [languageName], as opposed to the language merely appearing in
    // the concept registry because some node's concept comes from it.
    private fun usesLanguage(model: Path, languageName: String): Boolean =
        languagesBlock(model).contains("name=\"$languageName\"")

    private fun importsModel(model: Path, modelName: String): Boolean =
        importsBlock(model).contains("($modelName)")

    private companion object {
        const val STRUCTURE_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val STRUCTURE_MODEL_PATH =
            "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"

        // A solution model that uses only platform build languages (no project language), so it is freely editable in
        // the test environment and does not use baseLanguage.
        const val BUILD_MODEL = "r:1044fb59-f691-4b27-8b09-aa9b966feb0e(com.specificlanguages.json.build)"
        const val BUILD_MODEL_PATH =
            "solutions/com.specificlanguages.json.build/models/com.specificlanguages.json.build.mps"

        const val SANDBOX_MODEL = "r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)"
        const val SANDBOX_MODEL_NAME = "json.sandbox"
        // The JsonFile root of the sandbox model.
        const val SANDBOX_ROOT_ID = "4Twci\$d7zxq"

        // The PropertyDeclaration "value" of JsonString; carries a `dataType` reference.
        const val JSON_STRING_VALUE_ID = "2110045694544569338"
        // The JsonArray ConceptDeclaration; accepts children in the `propertyDeclaration` role.
        const val JSON_ARRAY_ID = "2110045694544569357"

        const val CONCEPT_DECLARATION = "jetbrains.mps.lang.structure.structure.ConceptDeclaration"
        const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
        const val BASE_LANGUAGE = "jetbrains.mps.baseLanguage"
    }
}
