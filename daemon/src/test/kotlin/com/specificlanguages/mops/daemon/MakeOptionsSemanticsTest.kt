package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
import jetbrains.mps.generator.GenerationSettingsProvider
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.MPSProject
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives the real make with generation options and checks what they change: deleted stale output, reported info
 * messages, kept transient models, and the performance report. Also checks that the process-wide generation settings
 * a make sets for itself are restored afterwards.
 */
class MakeOptionsSemanticsTest {
    @Test
    fun `a make deletes files in the made modules' output folders that it no longer generates`() {
        SharedMpsEnvironment.withProjectCopy(prepare = ::addStrayOutput) { access, projectPath ->
            val response = access.extra { makeModules(listOf("json.sandbox")) }

            assertSucceeded(response)
            assertFalse(projectPath.resolve(SANDBOX_STRAY).exists(), "the sandbox's stray file must be deleted")
            assertFalse(projectPath.resolve(LANGUAGE_STRAY).exists(), "the language's stray file must be deleted too")
        }
    }

    @Test
    fun `the make used by test runs and Code Mode deletes files it no longer generates`() {
        SharedMpsEnvironment.withOpenProjectCopy(prepare = ::addStrayOutput) { project, projectPath ->
            val response = ProjectMake(project).makeModules(listOf("json.sandbox"))

            assertSucceeded(response)
            assertFalse(projectPath.resolve(SANDBOX_STRAY).exists(), "the sandbox's stray file must be deleted")
        }
    }

    @Test
    fun `info messages from every make step are reported only with the info message level`() {
        val (plain, warning, info) = SharedMpsEnvironment.withProjectCopy { access, _ ->
            fun make(level: MakeMessageLevel?) =
                access.extra { makeModules(listOf("json.sandbox"), MakeGenerationOptions(messages = level)) }
            Triple(make(null), make(MakeMessageLevel.WARNING), make(MakeMessageLevel.INFO))
        }

        listOf(plain, warning, info).forEach(::assertSucceeded)
        assertTrue(plain.messages.none { it.kind == MakeMessageKind.INFO }, "got ${plain.messages}")
        assertTrue(warning.messages.none { it.kind == MakeMessageKind.INFO }, "got ${warning.messages}")
        assertTrue(
            info.messages.any { it.kind == MakeMessageKind.INFO && it.text.contains("json.sandbox") },
            "expected generator info about the sandbox model, got ${info.messages}",
        )
        // The make script, not the generator, reports how long each make target took.
        assertTrue(
            info.messages.any { it.kind == MakeMessageKind.INFO && it.text.contains("target execution time") },
            "expected make target timing info, got ${info.messages}",
        )
    }

    @Test
    fun `performance tracing adds a performance report`() {
        val response = SharedMpsEnvironment.withProjectCopy { access, _ ->
            val options = MakeGenerationOptions(
                messages = MakeMessageLevel.INFO,
                performanceTracing = PerformanceTracingLevel.STEPS,
            )
            access.extra { makeModules(listOf("json.sandbox"), options) }
        }

        assertSucceeded(response)
        assertTrue(
            response.messages.any { it.kind == MakeMessageKind.INFO && it.text.contains("Performance report") },
            "expected a performance report, got ${response.messages}",
        )
    }

    @Test
    fun `saved transient models stay in the repository after the make, and the settings are restored`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val access = SharedMpsEnvironment.mpsAccess(project)

            access.extra { makeModules(listOf("json.sandbox")) }
            assertEquals(emptyList(), transientModules(project), "without the option, transient models are dropped")

            val response = access.extra {
                makeModules(listOf("json.sandbox"), MakeGenerationOptions(saveTransientModels = true))
            }

            assertSucceeded(response)
            assertTrue(transientModules(project).any { it.startsWith("json.sandbox@transient") }, "${transientModules(project)}")
            assertFalse(settings(project).isSaveTransientModels, "the make must restore the generation settings")
        }
    }

    @Test
    fun `a cancelled make restores the generation settings`() {
        SharedMpsEnvironment.withOpenProjectCopy { project, _ ->
            val cancelled = object : EmptyProgressMonitor() {
                override fun isCanceled() = true
            }
            val options = MakeGenerationOptions(
                saveTransientModels = true,
                performanceTracing = PerformanceTracingLevel.TYPES,
            )

            ProjectMake(project).makeModules(listOf("json.sandbox"), cancelled, options)

            assertFalse(settings(project).isSaveTransientModels)
            assertEquals(0, settings(project).performanceTracingLevel)
        }
    }

    @Test
    fun `a failed make restores the generation settings`() {
        SharedMpsEnvironment.withOpenProjectCopy(projectName = SANDBOX, prepare = ::breakCalculator) { project, _ ->
            val options = MakeGenerationOptions(saveTransientModels = true)

            val response = ProjectMake(project).makeProject(options)

            assertEquals(MakeOutcome.FAILED, response.outcome)
            assertFalse(settings(project).isSaveTransientModels)
        }
    }

    private fun settings(project: MPSProject) =
        project.getComponent(GenerationSettingsProvider::class.java).generationSettings

    private fun transientModules(project: MPSProject): List<String> =
        project.modelAccess.computeReadAction<List<String>> {
            project.repository.modules.map { it.moduleName.orEmpty() }.filter { it.contains("@transient") }
        }

    private fun assertSucceeded(response: MakeResponse) {
        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertEquals(MakeOutcome.SUCCESS, response.outcome, "unexpected errors: $errors")
    }

    private fun addStrayOutput(projectPath: Path) {
        for (stray in listOf(SANDBOX_STRAY, LANGUAGE_STRAY)) {
            projectPath.resolve(stray).also { it.parent.createDirectories() }.writeText("not generated by any model")
        }
    }

    // Removes an operand from Calculator.add's `a + b` body, so the sandbox no longer makes.
    private fun breakCalculator(projectPath: Path) {
        val model = projectPath.resolve("solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps")
        val original = model.readText()
        val broken = original.replace(
            Regex("""\s*<node concept="37vLTw" id="4LxqAFFLH4T".*?</node>""", RegexOption.DOT_MATCHES_ALL),
            "",
        )
        require(broken != original) { "expected to remove the `a` operand from the sandbox model" }
        model.writeText(broken)
    }

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val SANDBOX_STRAY = "solutions/json.sandbox/source_gen/stray/Stray.txt"
        const val LANGUAGE_STRAY = "languages/com.specificlanguages.json/source_gen/stray/Stray.txt"
    }
}
