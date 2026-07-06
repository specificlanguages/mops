package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.module.ModelAccess
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.function.Supplier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * An [MpsRequestException] is an expected client error, so it must be captured inside the MPS read/write computation
 * and only rethrown once that computation has returned. If it escaped the computation directly, MPS's ActionDispatcher
 * would log it as a SEVERE "Action dispatch failed" before rethrowing, flooding the daemon log with stack traces for
 * routine failures such as an unresolved concept.
 *
 * These tests stand in for the model-access boundary with mocks — the real dispatcher only logs (it does not throw) in
 * the test environment, so a boot-backed test could not observe the escape — and assert that the computation MPS runs
 * never sees the exception.
 */
class ModelAccessBoundaryTest {

    private val requestFailure = MpsRequestException(MpsErrorCode.CONCEPT_NOT_FOUND, "concept not found: some.Concept")

    @Test
    fun `read captures a request error inside the read computation and rethrows it to the caller`() {
        var escapedComputation: Throwable? = null
        val modelAccess = mock<ModelAccess>()
        whenever(modelAccess.computeReadAction(any<Supplier<Any?>>())).thenAnswer { invocation ->
            val computation = invocation.getArgument<Supplier<Any?>>(0)
            recordingEscape({ escapedComputation = it }, computation::get)
        }
        val access = boundaryAccess(mock { on { this.modelAccess } doReturn modelAccess })

        val exception = assertFailsWith<MpsRequestException> {
            access.read { throw requestFailure }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertNull(escapedComputation, "the request error must not escape the read computation MPS dispatches")
    }

    @Test
    fun `write captures a request error inside the write computation and rethrows it to the caller`() {
        var escapedComputation: Throwable? = null
        val writeTransaction = mock<WriteTransaction>()
        whenever(writeTransaction.run(any(), any<WriteTransaction.WriteScope.() -> Any?>())).thenAnswer { invocation ->
            val body = invocation.getArgument<WriteTransaction.WriteScope.() -> Any?>(1)
            recordingEscape({ escapedComputation = it }) { WriteTransaction.WriteScope.body() }
        }
        val access = boundaryAccess(mock(), writeTransaction)

        val exception = assertFailsWith<MpsRequestException> {
            access.write { throw requestFailure }
        }

        assertEquals(MpsErrorCode.CONCEPT_NOT_FOUND, exception.code)
        assertNull(escapedComputation, "the request error must not escape the write computation MPS dispatches")
    }

    private fun boundaryAccess(
        project: Project,
        writeTransaction: WriteTransaction = mock(),
    ): JetBrainsMpsAccess =
        JetBrainsMpsAccess(
            project = project,
            logger = DaemonLogger(),
            mpsListExporter = mock(),
            jsonNodeExporter = mock(),
            modelNodeResolver = mock(),
            editBatchExecutor = mock(),
            persistence = mock(),
            writeTransaction = writeTransaction,
        )

    // Runs the computation MPS would dispatch, recording any throwable that escapes it (which is exactly what the
    // dispatcher logs as SEVERE), then rethrows so the caller-facing behavior is unchanged.
    private inline fun <T> recordingEscape(onEscape: (Throwable) -> Unit, computation: () -> T): T =
        try {
            computation()
        } catch (throwable: Throwable) {
            onEscape(throwable)
            throw throwable
        }
}
