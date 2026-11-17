package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import jetbrains.mps.make.script.IResult
import kotlin.test.Test
import kotlin.test.assertEquals

/** The outcome and messages of a make, from the MPS make result and the messages the make reported. */
class MakeResponseTest {
    private val warning = MakeMessageJson(MakeMessageKind.WARNING, "a warning")
    private val error = MakeMessageJson(MakeMessageKind.ERROR, "an error")
    private val info = MakeMessageJson(MakeMessageKind.INFO, "an info")

    @Test
    fun `a successful result without errors succeeds with the reported messages`() {
        val response = makeResponse(IResult.SUCCESS(null), listOf(info, warning), moduleCount = 2, level = null)

        assertEquals(MakeResponse(MakeOutcome.SUCCESS, 2, listOf(warning)), response)
    }

    @Test
    fun `a successful result with an error fails with that error`() {
        val response = makeResponse(IResult.SUCCESS(null), listOf(warning, error), moduleCount = 1, level = null)

        assertEquals(MakeResponse(MakeOutcome.FAILED, 1, listOf(warning, error)), response)
    }

    @Test
    fun `a failed result without an error message fails with the reason from the result`() {
        val response = makeResponse(IResult.FAILURE("not started", null), listOf(warning), moduleCount = 1, level = null)

        val reason = MakeMessageJson(MakeMessageKind.ERROR, "make failed without reporting an error: not started")
        assertEquals(MakeResponse(MakeOutcome.FAILED, 1, listOf(warning, reason)), response)
    }

    @Test
    fun `a missing result fails with an error even at the error message level`() {
        val response = makeResponse(null, listOf(warning), moduleCount = 1, level = MakeMessageLevel.ERROR)

        val reason = MakeMessageJson(MakeMessageKind.ERROR, "make failed without reporting an error")
        assertEquals(MakeResponse(MakeOutcome.FAILED, 1, listOf(reason)), response)
    }
}
