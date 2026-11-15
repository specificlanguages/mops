package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.CreateModelRequest
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Every path that resolves a module target reports a miss and an ambiguity with the same error. */
class ModuleTargetResolutionTest {

    @Test
    fun `every module target path reports an ambiguous name with the same error`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::addSecondSandbox) { project, _ ->
            val access = SharedMpsEnvironment.mpsAccess(project)
            val failures = mapOf(
                "get module" to { access.read { getModule(SANDBOX) } },
                "diagnose module" to { access.read { diagnoseModule(SANDBOX) } },
                "check module" to { access.read { checkModules(listOf(SANDBOX), 0) } },
                "make module" to { access.extra { makeModules(listOf(SANDBOX)) } },
                "list a model of the module" to { access.read { list(listOf(SANDBOX, SANDBOX), depth = 1) } },
                "create model" to { access.write { createModel(CreateModelRequest("", "$SANDBOX.extra", SANDBOX, dryRun = true)) } },
            ).mapValues { (_, call) -> assertFailsWith<MpsRequestException> { call() } }

            failures.forEach { (path, failure) ->
                assertEquals(MpsErrorCode.AMBIGUOUS_TARGET, failure.code, path)
                assertEquals(AMBIGUOUS_MESSAGE, failure.message, path)
            }

            val executor = CodeModeExecutor(access, project, SharedMpsEnvironment.platform)
            listOf("mops.lookup.module('$SANDBOX')", "mops.lookup.requireModule('$SANDBOX')").forEach { call ->
                val failure = assertFailsWith<IllegalStateException> {
                    executor.execute(CodeRunRequest("", "project.read { $call }", "lookup.groovy"))
                }
                assertEquals("lookup.groovy:1: $AMBIGUOUS_MESSAGE", failure.message, call)
            }
        }
    }

    @Test
    fun `every module target path reports a missing module with the same error`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = SharedMpsEnvironment.mpsAccess(project)
            val inRepository = "module not found in repository: $MISSING"
            val inProject = "module not found in project: $MISSING"
            val failures = listOf(
                inRepository to { access.read { getModule(MISSING) } },
                inRepository to { access.write { createModel(CreateModelRequest("", "a.model", MISSING, dryRun = true)) } },
                inProject to { access.read { checkModules(listOf(MISSING), 0) } },
                inProject to { access.extra { makeModules(listOf(MISSING)) } },
            )
            failures.forEach { (message, call) ->
                val failure = assertFailsWith<MpsRequestException> { call() }
                assertEquals(MpsErrorCode.MODULE_NOT_FOUND, failure.code, message)
                assertEquals(message, failure.message)
            }

            assertFalse(access.read { diagnoseModule(MISSING) }.module.present)

            val failure = assertFailsWith<IllegalStateException> {
                CodeModeExecutor(access, project, SharedMpsEnvironment.platform)
                    .execute(CodeRunRequest("", "project.read { mops.lookup.requireModule('$MISSING') }", "lookup.groovy"))
            }
            assertContains(failure.message.orEmpty(), inRepository)
        }
    }

    private fun addSecondSandbox(project: java.nio.file.Path) {
        // A second solution named json.sandbox with its own id and no models: same name, different module.
        val original = project.resolve("solutions/json.sandbox/json.sandbox.msd")
        val duplicate = project.resolve("solutions/json.sandbox.copy/json.sandbox.msd")
        duplicate.parent.toFile().mkdirs()
        duplicate.writeText(original.readText().replace(SANDBOX_ID, DUPLICATE_ID))
        val modules = project.resolve(".mps/modules.xml")
        modules.writeText(
            modules.readText().replace(
                "    </projectModules>",
                "      <modulePath path=\"\$PROJECT_DIR\$/solutions/json.sandbox.copy/json.sandbox.msd\" folder=\"\" />\n" +
                    "    </projectModules>",
            ),
        )
    }

    private companion object {
        const val SANDBOX = "json.sandbox"
        const val MISSING = "no.such.module"
        const val SANDBOX_ID = "84cf8fb2-5a00-4f48-8747-6b797fc155f2"
        const val DUPLICATE_ID = "11111111-2222-4333-8444-555555555555"
        val AMBIGUOUS_MESSAGE = "ambiguous module target $SANDBOX:\n" +
            "module\t$SANDBOX\t$DUPLICATE_ID($SANDBOX)\n" +
            "module\t$SANDBOX\t$SANDBOX_ID($SANDBOX)"
    }
}
