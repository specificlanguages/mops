package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.protocol.CodeRunRequest
import com.specificlanguages.mops.protocol.ConstraintEnforcement
import jetbrains.mps.project.Project
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class CodeModeExecutorTest {
    private val executor = CodeModeExecutor(mock<MpsAccess>(), mock<Project>())

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

    private fun run(source: String): String? = executor.execute(
        CodeRunRequest("token", source, "test.groovy", ConstraintEnforcement.BEST_EFFORT),
    ).output
}
