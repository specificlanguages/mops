package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FindingSeverity
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runs the real MPS Model Check against a copy of the `base-language-sandbox` fixture. Its Calculator model is valid
 * baseLanguage — a bundled, loaded language — so the check runs its typesystem and reference rules for real and reports
 * a deliberately broken reference the way MPS's own checker does.
 */
class ModelCheckSemanticsTest {

    @Test
    fun `a clean model reports no error findings`() {
        val response = SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { access, _ ->
            access.read { checkModel(MODEL_NAME, limit = 0) }
        }

        val errors = response.findings.filter { it.severity == FindingSeverity.ERROR }
        assertTrue(errors.isEmpty(), "the clean Calculator model must report no errors; got $errors")
    }

    @Test
    fun `a broken reference is reported as an error naming the offending node`() {
        val response = SharedMpsEnvironment.withProjectCopy(
            projectName = SANDBOX,
            prepare = ::breakReferenceToParameterA,
        ) { access, _ ->
            access.read { checkModel(MODEL_NAME, limit = 0) }
        }

        val errors = response.findings.filter { it.severity == FindingSeverity.ERROR }
        assertTrue(errors.isNotEmpty(), "the broken reference must be reported as an error; got ${response.findings}")

        // Totals count the full finding set; the unbounded run shows everything, so they match the findings.
        assertEquals(errors.size, response.totals.errors)
        assertEquals(response.findings.size, response.totals.total)

        // The offending node is the VariableReference whose target no longer resolves.
        val finding = errors.single { it.message.contains("Unresolved reference") }
        val node = assertNotNull(finding.node, "the finding must name the offending node")
        assertEquals("jetbrains.mps.baseLanguage.structure.VariableReference", node.concept)
        assertContains(node.reference, MODEL_REFERENCE)
    }

    @Test
    fun `an unknown model fails with a model-not-found error`() {
        val failure = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { access, _ ->
                access.read { checkModel("no.such.model", limit = 0) }
            }
        }

        assertEquals(MpsErrorCode.MODEL_NOT_FOUND, failure.code)
    }

    @Test
    fun `project check checks models in every Project Module`() {
        val response = SharedMpsEnvironment.withProjectCopy(
            projectName = SANDBOX,
            prepare = ::breakReferenceToParameterA,
        ) { access, _ ->
            access.read { checkProject(limit = 0) }
        }

        assertTrue(response.findings.any { it.severity == FindingSeverity.ERROR })
    }

    @Test
    fun `module check checks models in the named Project Module`() {
        val response = SharedMpsEnvironment.withProjectCopy(
            projectName = SANDBOX,
            prepare = ::breakReferenceToParameterA,
        ) { access, _ ->
            access.read { checkModules(listOf(MODULE_NAME), limit = 0) }
        }

        assertTrue(response.findings.any { it.severity == FindingSeverity.ERROR })
    }

    @Test
    fun `module check combines findings from every named Project Module`() {
        val responses = SharedMpsEnvironment.withProjectCopy { access, _ ->
            val first = access.read { checkModules(listOf(JSON_LANGUAGE), limit = 0) }
            val second = access.read { checkModules(listOf(JSON_SANDBOX), limit = 0) }
            val combined = access.read { checkModules(listOf(JSON_LANGUAGE, JSON_SANDBOX), limit = 0) }
            Triple(first, second, combined)
        }

        val expectedFindings = (responses.first.findings + responses.second.findings)
            .groupingBy { it }
            .eachCount()
        val actualFindings = responses.third.findings.groupingBy { it }.eachCount()
        assertEquals(expectedFindings, actualFindings)
        assertEquals(responses.first.totals.total + responses.second.totals.total, responses.third.totals.total)
    }

    @Test
    fun `project check applies one global limit after collecting all findings`() {
        val response = SharedMpsEnvironment.withProjectCopy(
            projectName = SANDBOX,
            prepare = ::breakTwoReferences,
        ) { access, _ ->
            access.read { checkProject(limit = 1) }
        }

        assertEquals(1, response.findings.size)
        assertTrue(response.totals.errors >= 2)
        assertTrue(response.truncated)
    }

    @Test
    fun `module check rejects a dependency module outside the project`() {
        val failure = assertFailsWith<MpsRequestException> {
            SharedMpsEnvironment.withProjectCopy(projectName = SANDBOX) { access, _ ->
                access.read { checkModules(listOf("JDK"), limit = 0) }
            }
        }

        assertEquals(MpsErrorCode.TARGET_NOT_FOUND, failure.code)
    }

    // Repoints the `add` body's reference to parameter `a` at a node ID that no longer exists, leaving the
    // VariableReference dangling. Only the ref site uses node="4LxqAFFLH2G"; the parameter itself uses id="...", so it
    // stays intact and the model still loads.
    private fun breakReferenceToParameterA(projectPath: Path) {
        val model = projectPath.resolve("solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps")
        val original = model.readText()
        val broken = original.replace("node=\"4LxqAFFLH2G\"", "node=\"9999999999999999999\"")
        require(broken != original) { "expected to repoint the reference to parameter a" }
        model.writeText(broken)
    }

    private fun breakTwoReferences(projectPath: Path) {
        val model = projectPath.resolve("solutions/baselanguage.sandbox/models/baselanguage.sandbox.mps")
        val original = model.readText()
        val broken = original
            .replace("node=\"4LxqAFFLH2G\"", "node=\"9999999999999999999\"")
            .replace("node=\"4LxqAFFLRdb\"", "node=\"9999999999999999998\"")
        require(broken != original) { "expected to repoint two parameter references" }
        model.writeText(broken)
    }

    private companion object {
        const val SANDBOX = "base-language-sandbox"
        const val MODEL_NAME = "baselanguage.sandbox"
        const val MODULE_NAME = "baselanguage.sandbox"
        const val JSON_LANGUAGE = "com.specificlanguages.json"
        const val JSON_SANDBOX = "json.sandbox"
        const val MODEL_REFERENCE = "r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)"
    }
}
