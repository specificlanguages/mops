package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.CreateModelRequest
import com.specificlanguages.mops.protocol.CreateSolutionRequest
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.EditOperation
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeModulesRequest
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ModelEditRequest
import com.specificlanguages.mops.protocol.ModelDestination
import com.specificlanguages.mops.protocol.MpsNodePropertyJson
import com.specificlanguages.mops.protocol.SolutionUsagePreset
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Drives the real make against fixture-project copies (make writes generated files, so the shared read-only project
 * cannot be used). Asserts that making a solution automatically makes the language it depends on, that a whole-project
 * make succeeds, that a module whose closure reaches jar-packaged library sources still makes, that a broken model
 * fails with reported errors, and that modules and models with nothing to generate are left out of the make.
 */
class MakeSemanticsTest {
    @Test
    fun `making a solution also makes the language it depends on`() {
        val response = SharedMpsEnvironment.withProjectCopy { access, _ ->
            access.extra {makeModules(listOf("json.sandbox")) }
        }

        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertEquals(MakeOutcome.SUCCESS, response.outcome, "unexpected errors: $errors")
        // The sandbox plus its dependency closure's generatable modules: at least the sandbox and the json language.
        assertTrue(response.moduleCount >= 2, "expected the language to be made too, got ${response.moduleCount}")
    }

    @Test
    fun `making a module whose closure reaches jar-packaged library sources succeeds`() {
        // `exprs` uses baseLanguage.closures/collections/tuples, whose runtime solutions MPS ships as source models
        // inside `-src.jar` files. Those models report themselves generatable, so a naive make set includes them and
        // fails trying to write generation output into the jar ("Write for jar files is not supported"). The make must
        // drop read-only library modules and generate only the writable project sources.
        val response = SharedMpsEnvironment.withProjectCopy(projectName = EXPRESSION_LANGUAGE) { access, _ ->
            access.extra { makeModules(listOf("exprs")) }
        }

        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertEquals(MakeOutcome.SUCCESS, response.outcome, "unexpected errors: $errors")
        assertTrue(
            errors.none { it.text.contains("Write for jar files") },
            "the make must not try to regenerate jar-packaged library modules; got ${errors.map { it.text }}",
        )
    }

    @Test
    fun `a make with several generation threads succeeds`() {
        val response = SharedMpsEnvironment.withProjectCopy { access, _ ->
            access.extra { makeModules(listOf("json.sandbox"), MakeGenerationOptions(threads = 2)) }
        }

        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertEquals(MakeOutcome.SUCCESS, response.outcome, "unexpected errors: $errors")
        assertTrue(response.moduleCount >= 2, "expected the language to be made too, got ${response.moduleCount}")
    }

    @Test
    fun `making the whole project succeeds`() {
        val response = SharedMpsEnvironment.withProjectCopy { access, _ ->
            access.extra {makeProject() }
        }

        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertEquals(MakeOutcome.SUCCESS, response.outcome, "unexpected errors: $errors")
        assertTrue(response.moduleCount >= 2)
    }

    @Test
    fun `a make that does not compile fails and reports the errors`() {
        val response = SharedMpsEnvironment.withProjectCopy(
            projectName = SANDBOX,
            prepare = ::breakCalculator,
        ) { access, _ ->
            access.extra {makeProject() }
        }

        assertEquals(
            MakeOutcome.FAILED,
            response.outcome,
            "the broken sandbox must not make successfully",
        )
        val errors = response.messages.filter { it.kind == MakeMessageKind.ERROR }
        assertTrue(errors.isNotEmpty(), "a failing make must report at least one error; got ${response.messages}")
        // The failure is the broken Calculator — generator and Java-compiler errors both surface through the handler.
        assertTrue(
            errors.any { it.text.contains("Calculator") },
            "expected an error about the broken Calculator; got ${errors.map { it.text }}",
        )
    }

    // Removes the reference to parameter `a` from Calculator.add's `a + b` body, leaving the `+` with an empty
    // required left operand, so the sandbox no longer makes.
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

    // The tests below send each step as its own daemon request, as the CLI does.
    @Test
    fun `a solution with no generation-target facets makes without generating its models`() {
        val (response, solutionDir) = SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val daemon = DomainRequestHandler(projectPath, SharedMpsEnvironment.mpsAccess(project))
            createSolutionWithModel(daemon, "example.analyzed", SolutionUsagePreset.NOT_GENERATED)
            val addClass = EditOperation.AddRoot(
                model = ModelDestination("example.analyzed.main"),
                concept = CLASS_CONCEPT,
                properties = listOf(MpsNodePropertyJson(name = "name", value = "Analyzed")),
            )
            daemon.handleDomainRequest(ModelEditRequest("", EditBatch(listOf(addClass))))
            daemon.make("example.analyzed") to projectPath.resolve("solutions/example.analyzed")
        }

        assertEquals(MakeOutcome.NOTHING_TO_GENERATE, response.outcome, "got ${response.messages}")
        assertTrue(
            response.messages.any { it.kind == MakeMessageKind.WARNING && it.text.contains("example.analyzed") },
            "expected a warning naming the module left out; got ${response.messages}",
        )
        assertFalse(solutionDir.resolve("source_gen").exists(), "the solution's models must not be generated")
    }

    @Test
    fun `a solution with no generation-target facets and an empty model has nothing to generate`() {
        val response = SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val daemon = DomainRequestHandler(projectPath, SharedMpsEnvironment.mpsAccess(project))
            createSolutionWithModel(daemon, "example.analyzed", SolutionUsagePreset.NOT_GENERATED)
            daemon.make("example.analyzed")
        }

        assertEquals(MakeOutcome.NOTHING_TO_GENERATE, response.outcome, "got ${response.messages}")
    }

    @Test
    fun `a generated solution whose only model is empty has nothing to generate`() {
        val response = SharedMpsEnvironment.withOpenProjectCopy { project, projectPath ->
            val daemon = DomainRequestHandler(projectPath, SharedMpsEnvironment.mpsAccess(project))
            createSolutionWithModel(daemon, "example.empty", SolutionUsagePreset.JAVA)
            daemon.make("example.empty")
        }

        assertEquals(MakeOutcome.NOTHING_TO_GENERATE, response.outcome, "got ${response.messages}")
    }

    private fun createSolutionWithModel(daemon: DomainRequestHandler, name: String, preset: SolutionUsagePreset) {
        daemon.handleDomainRequest(CreateSolutionRequest("", name, usagePreset = preset))
        daemon.handleDomainRequest(CreateModelRequest("", "$name.main", name))
    }

    private fun DomainRequestHandler.make(
        module: String,
        generation: MakeGenerationOptions = MakeGenerationOptions(),
    ): MakeResponse = assertIs<MakeResponse>(handleDomainRequest(MakeModulesRequest("", listOf(module), generation)))

    @Test
    fun `an unknown module name fails with a target-not-found error`() {
        val failure = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.withProjectCopy { access, _ ->
                access.extra {makeModules(listOf("no.such.module.at.all")) }
            }
        }

        assertEquals(MpsErrorCode.TARGET_NOT_FOUND, failure.code)
    }

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val EXPRESSION_LANGUAGE = "expression-language"
        const val CLASS_CONCEPT = "jetbrains.mps.baseLanguage.structure.ClassConcept"
    }
}
