package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.ProtocolJson
import com.specificlanguages.mops.protocol.TestRunReport
import jetbrains.mps.core.platform.Platform
import jetbrains.mps.project.MPSProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class CodeModeExecutorTest {
    private val executor = CodeModeExecutor(mock<MpsAccess>(), mock<MPSProject>(), mock<Platform>())

    @Test
    fun `adapts strings structured values primitives and null`() {
        assertEquals("hello", run("'hello'"))
        assertEquals("{\"answer\":42,\"items\":[true,null]}", run("[answer: 42, items: [true, null]]"))
        assertEquals("7", run("7"))
        assertNull(run("null"))
    }

    @Test
    fun `each invocation receives a fresh binding`() {
        assertEquals("set", run("leaked = 'value'; 'set'"))
        val failure = assertFailsWith<Throwable> { run("leaked") }
        assertTrue(failure.message.orEmpty().contains("leaked"))
    }

    @Test
    fun `dependency injection transforms are rejected`() {
        val failure = assertFailsWith<IllegalArgumentException> { run("@Grab('x:y:1')\nreturn 1") }
        assertTrue(failure.message.orEmpty().contains("dependency injection"))
    }

    @Test
    fun `unsupported values identify their JVM class`() {
        val failure = assertFailsWith<IllegalStateException> { run("new StringBuilder()") }
        assertTrue(failure.message.orEmpty().contains("java.lang.StringBuilder"))
    }

    @Test
    fun `make results retain outcome counts and escaped diagnostic messages`() {
        val result = MakeResponse(
            MakeOutcome.FAILED,
            moduleCount = 2,
            messages = listOf(MakeMessageJson(MakeMessageKind.ERROR, "Missing \"dependency\"\nRetry")),
        )
        val expected = """{"outcome":"FAILED","moduleCount":2,"messages":[{"kind":"ERROR","text":"Missing \"dependency\"\nRetry"}]}"""
        assertEquals(expected, CodeResultAdapter.render(result))
        assertEquals("{\"build\":[$expected,null]}", CodeResultAdapter.render(mapOf("build" to listOf(result, null))))
    }

    @Test
    fun `test reports retain embedded make results both directly and in collections`() {
        val report = TestRunReport(
            reportPath = "/tmp/tests.json",
            outcome = "BUILD_FAILED",
            complete = true,
            build = MakeResponse(MakeOutcome.FAILED, 1, listOf(MakeMessageJson(MakeMessageKind.ERROR, "Build failed"))),
            diagnostics = listOf("Tests skipped"),
        )
        val rendered = CodeResultAdapter.render(report)!!
        assertEquals(report, ProtocolJson.decodeTestReport(rendered))
        assertEquals("{\"tests\":[$rendered]}", CodeResultAdapter.render(mapOf("tests" to listOf(report))))
    }

    private fun run(source: String): String? = executor.execute(
        CodeRunRequest("token", source, "test.groovy"),
    ).output
}
