package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MpsListEntryJson
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MpsListSemanticsTest {

    @Test
    fun `lists current project entity`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read { list(null, depth = 0) }

        assertEquals(MpsListEntryJson(type = "project", name = "mps-json"), root)
    }

    @Test
    fun `lists project modules by default`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read { list(null, depth = 1) }

        assertEquals(
            MpsListEntryJson(
                type = "project",
                name = "mps-json",
                children = listOf(
                    MpsListEntryJson(
                        type = "module",
                        name = "com.specificlanguages.json",
                        moduleKind = "language",
                        reference = LANGUAGE_MODULE_REFERENCE,
                    ),
                    MpsListEntryJson(
                        type = "module",
                        name = "com.specificlanguages.json.build",
                        moduleKind = "solution",
                        reference = BUILD_MODULE_REFERENCE,
                    ),
                    MpsListEntryJson(
                        type = "module",
                        name = "json.sandbox",
                        moduleKind = "solution",
                        reference = SANDBOX_MODULE_REFERENCE,
                    ),
                ),
            ),
            root,
        )
    }

    @Test
    fun `lists repository entity`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read { list(listOf("/"), depth = 0) }

        assertEquals(MpsListEntryJson(type = "repository", name = "/"), root)
    }

    @Test
    fun `lists repository modules`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read { list(listOf("/"), depth = 1) }

        assertEquals("repository", root.type)
        val modules = assertNotNull(root.children)
        assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json" })
        assertNotNull(modules.singleOrNull { it.name == "com.specificlanguages.json.build" })
        assertNotNull(modules.singleOrNull { it.name == "MPS.Core" })
    }

    @Test
    fun `lists models owned by project module`() {
        val module = SharedMpsEnvironment.sharedMpsAccess.read { list(listOf("com.specificlanguages.json"), depth = 1) }

        assertEquals("module", module.type)
        assertEquals("com.specificlanguages.json", module.name)
        assertEquals("language", module.moduleKind)
        assertEquals(LANGUAGE_MODULE_REFERENCE, module.reference)

        val models = assertNotNull(module.children)
        val structure = models.single { it.name == "com.specificlanguages.json.structure" }
        assertEquals(
            MpsListEntryJson(
                type = "model",
                name = "com.specificlanguages.json.structure",
                reference = STRUCTURE_MODEL_REFERENCE,
            ),
            structure,
        )
    }

    @Test
    fun `lists root nodes owned by model path`() {
        val model = SharedMpsEnvironment.sharedMpsAccess.read {
            list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"), depth = 1)
        }

        assertEquals("model", model.type)
        assertEquals("com.specificlanguages.json.structure", model.name)
        assertEquals(STRUCTURE_MODEL_REFERENCE, model.reference)

        val roots = assertNotNull(model.children)
        val jsonFile = roots.single { it.name == "JsonFile" }
        assertEquals(
            MpsListEntryJson(
                type = "root",
                name = "JsonFile",
                concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
                id = JSON_FILE_NODE_ID,
                reference = JSON_FILE_NODE_REFERENCE,
            ),
            jsonFile,
        )
    }

    @Test
    fun `expands dot-prefixed model suffix after module target`() {
        val moduleTargets = listOf("com.specificlanguages.json", LANGUAGE_MODULE_REFERENCE)

        for (moduleTarget in moduleTargets) {
            val root = SharedMpsEnvironment.sharedMpsAccess.read {
                list(listOf(moduleTarget, ".structure", "JsonFile"), depth = 1)
            }

            assertEquals("root", root.type)
            assertEquals("JsonFile", root.name)
            assertEquals(JSON_FILE_NODE_ID, root.id)
            assertNotNull(root.children)
        }
    }

    @Test
    fun `lists root nodes owned by serialized model reference`() {
        val model = SharedMpsEnvironment.sharedMpsAccess.read { list(listOf(STRUCTURE_MODEL_REFERENCE), depth = 1) }

        assertEquals("model", model.type)
        assertEquals("com.specificlanguages.json.structure", model.name)
        assertEquals(STRUCTURE_MODEL_REFERENCE, model.reference)

        val roots = assertNotNull(model.children)
        val jsonFile = roots.single { it.name == "JsonFile" }
        assertEquals("root", jsonFile.type)
        assertEquals(JSON_FILE_NODE_ID, jsonFile.id)
    }

    @Test
    fun `lists child node addressed below serialized model reference`() {
        val node = SharedMpsEnvironment.sharedMpsAccess.read {
            list(listOf(BUILD_MODEL_REFERENCE, "com.specificlanguages.json", "version"), depth = 1)
        }

        assertEquals("node", node.type)
        assertEquals("version", node.name)
        assertEquals("jetbrains.mps.build.structure.BuildVariableMacro", node.concept)
        assertNotNull(node.id)
        assertNotNull(node.reference)
        assertNotNull(node.children)
    }

    @Test
    fun `lists containment children owned by root node path`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read {
            list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile"), depth = 1)
        }

        assertEquals("root", root.type)
        assertEquals("JsonFile", root.name)
        assertEquals(JSON_FILE_NODE_ID, root.id)

        val children = assertNotNull(root.children)
        val implements = children.single { it.role == "implements" }
        assertEquals("node", implements.type)
        assertNull(implements.name)
        assertEquals("jetbrains.mps.lang.structure.structure.InterfaceConceptReference", implements.concept)
        assertNotNull(implements.id)
        assertNotNull(implements.reference)
        assertNull(implements.children)
    }

    @Test
    fun `lists root node addressed by compact node id`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read {
            list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "1P8oQ4NaXDS"), depth = 1)
        }

        assertEquals("root", root.type)
        assertEquals("JsonFile", root.name)
        assertEquals(JSON_FILE_NODE_ID, root.id)
        assertNotNull(root.children)
    }

    @Test
    fun `lists root node addressed by serialized node reference`() {
        val root = SharedMpsEnvironment.sharedMpsAccess.read { list(listOf(JSON_FILE_NODE_REFERENCE), depth = 1) }

        assertEquals("root", root.type)
        assertEquals("JsonFile", root.name)
        assertEquals(JSON_FILE_NODE_ID, root.id)
        assertEquals(JSON_FILE_NODE_REFERENCE, root.reference)
        assertNotNull(root.children)
    }

    @Test
    fun `lists containment children owned by child node path`() {
        val node = SharedMpsEnvironment.sharedMpsAccess.read {
            list(
                listOf(
                    "com.specificlanguages.json.build",
                    "com.specificlanguages.json.build",
                    "com.specificlanguages.json",
                    "version",
                ),
                depth = 1,
            )
        }

        assertEquals("node", node.type)
        assertNull(node.role)
        assertEquals("version", node.name)
        assertEquals("jetbrains.mps.build.structure.BuildVariableMacro", node.concept)
        assertNotNull(node.id)
        assertNotNull(node.reference)

        val children = assertNotNull(node.children)
        val initialValue = children.single { it.role == "initialValue" }
        assertEquals("node", initialValue.type)
        assertNull(initialValue.name)
        assertEquals("jetbrains.mps.build.structure.BuildVariableMacroInitWithString", initialValue.concept)
        assertNotNull(initialValue.id)
        assertNotNull(initialValue.reference)
        assertNull(initialValue.children)
    }

    @Test
    fun `limits depth across module model root and child traversal`() {
        val module = SharedMpsEnvironment.sharedMpsAccess.read {
            list(listOf("com.specificlanguages.json.build"), depth = 4)
        }

        assertEquals("module", module.type)
        val models = assertNotNull(module.children)
        val model = models.single { it.name == "com.specificlanguages.json.build" }
        val roots = assertNotNull(model.children)
        val buildProject = roots.single { it.name == "com.specificlanguages.json" }
        val rootChildren = assertNotNull(buildProject.children)
        val version = rootChildren.single { it.name == "version" }
        val versionChildren = assertNotNull(version.children)
        val initialValue = versionChildren.single { it.role == "initialValue" }
        assertNull(initialValue.children)
    }

    @Test
    fun `fails instead of guessing when model target is ambiguous`() {
        val duplicateReference = "r:11111111-2222-4333-8444-555555555555(com.specificlanguages.json.structure)"

        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                model.resolveSibling("duplicate.structure.mps").writeText(
                    model.readText().replace(STRUCTURE_MODEL_REFERENCE, duplicateReference),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure"), depth = 1)
                }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous model target")
            assertContains(exception.message, STRUCTURE_MODEL_REFERENCE)
            assertContains(exception.message, duplicateReference)
        }
    }

    @Test
    fun `fails instead of guessing when module target is ambiguous`() {
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
                mpsAccess.read { list(listOf("com.specificlanguages.json.build"), depth = 1) }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous module target com.specificlanguages.json.build")
            assertContains(exception.message, BUILD_MODULE_REFERENCE)
            assertContains(exception.message, duplicateReference)
        }
    }

    @Test
    fun `fails instead of guessing when root node target is ambiguous`() {
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve(STRUCTURE_MODEL_PATH)
                model.writeText(
                    model.readText().replace(
                        """<property role="TrG5h" value="JsonObject" />""",
                        """<property role="TrG5h" value="JsonFile" />""",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    list(listOf("com.specificlanguages.json", "com.specificlanguages.json.structure", "JsonFile"), depth = 1)
                }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous root node target JsonFile")
            assertContains(exception.message, JSON_FILE_NODE_REFERENCE)
            assertContains(exception.message, "$STRUCTURE_MODEL_REFERENCE/2110045694544567020")
        }
    }

    @Test
    fun `fails instead of guessing when child node target is ambiguous`() {
        SharedMpsEnvironment.withProjectCopy(
            prepare = { project ->
                val model = project.resolve("solutions/com.specificlanguages.json.build/models/com.specificlanguages.json.build.mps")
                model.writeText(
                    model.readText().replace(
                        """<property role="TrG5h" value="mps_home" />""",
                        """<property role="TrG5h" value="version" />""",
                    ),
                )
            },
        ) { mpsAccess, _ ->
            val exception = assertFailsWith<MpsRequestException> {
                mpsAccess.read {
                    list(
                        listOf(
                            "com.specificlanguages.json.build",
                            "com.specificlanguages.json.build",
                            "com.specificlanguages.json",
                            "version",
                        ),
                        depth = 1,
                    )
                }
            }

            assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, exception.code)
            assertContains(exception.message, "ambiguous child node target version")
            assertContains(exception.message, "$BUILD_MODEL_REFERENCE/48805613928016575")
            assertContains(exception.message, "$BUILD_MODEL_REFERENCE/48805613928016630")
        }
    }

    private companion object {
        const val LANGUAGE_MODULE_REFERENCE = "f3f42ddf-d692-4c29-90fb-7360196f01ab(com.specificlanguages.json)"
        const val BUILD_MODULE_REFERENCE = "84f0ad52-c7ca-45dd-99c5-9605c96bf808(com.specificlanguages.json.build)"
        const val SANDBOX_MODULE_REFERENCE = "84cf8fb2-5a00-4f48-8747-6b797fc155f2(json.sandbox)"
        const val STRUCTURE_MODEL_REFERENCE = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"
        const val BUILD_MODEL_REFERENCE = "r:1044fb59-f691-4b27-8b09-aa9b966feb0e(com.specificlanguages.json.build)"
        const val JSON_FILE_NODE_ID = "2110045694544566904"
        const val JSON_FILE_NODE_REFERENCE = "$STRUCTURE_MODEL_REFERENCE/$JSON_FILE_NODE_ID"
        const val STRUCTURE_MODEL_PATH = "languages/com.specificlanguages.json/models/com.specificlanguages.json.structure.mps"
    }
}
