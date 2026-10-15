package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Resolution of a test selection target. It shares segment resolution with `mops list`, so these cases cover the
 * segments that selection accepts after a module.
 */
class TestSelectionResolutionTest {

    @Test
    fun `selects a module by name`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())

            val selection = access.read { access.resolveTestSelection(listOf(BUILD_MODULE_REFERENCE)) }

            assertEquals(BUILD_MODULE, assertIs<SModule>(selection).moduleName)
        }
    }

    @Test
    fun `selects the model named like its module through a bare dot segment`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())

            val selection = access.read { access.resolveTestSelection(listOf(BUILD_MODULE, ".")) }

            assertEquals(BUILD_MODULE, assertIs<SModel>(selection).name.value)
        }
    }

    @Test
    fun `reports a model segment that matches no model of the module with the module's models`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = JetBrainsMpsAccess(project, DaemonLogger())

            val exception = assertFailsWith<IllegalArgumentException> {
                access.read { access.resolveTestSelection(listOf(BUILD_MODULE, ".missing")) }
            }

            assertEquals(
                "test selection not found: $BUILD_MODULE .missing\n" +
                    "module $BUILD_MODULE has no model $BUILD_MODULE.missing; its models:\n" +
                    "model\t$BUILD_MODULE\t$BUILD_MODEL_REFERENCE",
                exception.message,
            )
        }
    }

    private companion object {
        const val BUILD_MODULE = "com.specificlanguages.json.build"
        const val BUILD_MODULE_REFERENCE = "84f0ad52-c7ca-45dd-99c5-9605c96bf808(com.specificlanguages.json.build)"
        const val BUILD_MODEL_REFERENCE = "r:1044fb59-f691-4b27-8b09-aa9b966feb0e(com.specificlanguages.json.build)"
    }
}
