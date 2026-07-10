package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Drives the real make against fixture-project copies (make writes generated files, so the shared read-only project
 * cannot be used). Asserts that making a solution automatically makes the language it depends on, that a whole-project
 * make succeeds, that a module whose closure reaches jar-packaged library sources still makes, and that a broken model
 * fails with reported errors.
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
    }
}
