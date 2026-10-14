package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The resolved Test Selection describes itself by the identity it resolved to, so a diagnostic about it names the
 * module, model, or root test case rather than echoing the caller's target segments.
 */
class AntTestSelectionTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `selection describes the resolved project module model or root test case`() {
        val access = SharedMpsEnvironment.sharedMpsAccess as JetBrainsMpsAccess
        fun description(vararg target: String) = AntTestSelection.resolve(access, target.toList()).description

        assertEquals("project (no target given)", description())
        assertEquals("module com.specificlanguages.json", description("com.specificlanguages.json"))
        assertEquals("model com.specificlanguages.json.structure", description("com.specificlanguages.json", ".structure"))
        assertEquals("root test case JsonFile in model com.specificlanguages.json.structure",
            description("com.specificlanguages.json", ".structure", "JsonFile"))
    }

    @Test
    fun `saved selection loads back unchanged`() {
        val selection = AntTestSelection(
            modules = listOf(Path.of("/project/solutions/tests/tests.msd")),
            moduleIds = listOf("85ebbead-a656-46af-af88-81c850c5e554"),
            libraries = listOf(Path.of("/project/solutions/tests/tests.msd"), Path.of("/project/solutions/peer/peer.msd")),
            model = "r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)",
            node = "r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)/4098",
            description = "root test case NativePass in model mops.tests.nativecases@tests",
        )
        val path = directory.resolve("selection.properties")

        selection.save(path)

        assertEquals(selection, AntTestSelection.load(path))
    }
}
