package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** The resolved Test Selection describes itself by the identity it resolved to: the project, a module, a model, or a root test case. */
class AntTestSelectionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `selection describes the resolved project module model or root test case`() {
        val access = SharedMpsEnvironment.sharedMpsAccess as JetBrainsMpsAccess
        fun description(vararg target: String) = AntTestSelection.resolve(access.project, target.toList()).description

        assertEquals("project (no target given)", description())
        assertEquals("module com.specificlanguages.json", description("com.specificlanguages.json"))
        assertEquals("model com.specificlanguages.json.structure", description("com.specificlanguages.json", ".structure"))
        assertEquals("root test case JsonFile in model com.specificlanguages.json.structure",
            description("com.specificlanguages.json", ".structure", "JsonFile"))
    }

    @Test
    fun `saved selection loads back unchanged`() {
        val selection = AntTestSelection(
            testModules = listOf(Path.of("/project/solutions/tests/tests.msd")),
            testModuleIds = listOf("85ebbead-a656-46af-af88-81c850c5e554"),
            repositoryModules = listOf(Path.of("/project/solutions/tests/tests.msd"), Path.of("/project/solutions/peer/peer.msd")),
            repositoryFolders = listOf(Path.of("/project/libs"), Path.of("/shared/libraries")),
            macros = mapOf("mops.fixture.project" to "/project", "shared.home" to "/shared"),
            model = "r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)",
            node = "r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)/4098",
            description = "root test case NativePass in model mops.tests.nativecases@tests",
            diagnostics = listOf("Project library external is not in the test worker's repository"),
        )
        val path = directory.resolve("selection.properties")

        selection.save(path)

        assertEquals(selection, AntTestSelection.load(path))
    }

    @Test
    fun `source folders are the project, the repository folders, and the module folders that no other source folder contains`() {
        val selection = AntTestSelection(
            testModules = listOf(Path.of("/project/solutions/tests/tests.msd")),
            testModuleIds = listOf("85ebbead-a656-46af-af88-81c850c5e554"),
            repositoryModules = listOf(Path.of("/project/solutions/tests/tests.msd"), Path.of("/elsewhere/peer/peer.msd"),
                Path.of("/shared/libraries/lib/lib.msd"), Path.of("/project-other/other.msd")),
            repositoryFolders = listOf(Path.of("/shared/libraries"), Path.of("/project/libs")),
            macros = emptyMap(), model = null, node = null, description = "project (no target given)", diagnostics = emptyList(),
        )

        assertEquals(listOf(Path.of("/project"), Path.of("/shared/libraries"), Path.of("/elsewhere/peer"),
            Path.of("/project-other")), selection.sourceFolders(Path.of("/project")))
    }
}
