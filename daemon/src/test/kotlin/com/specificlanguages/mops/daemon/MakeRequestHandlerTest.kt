package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsExtra
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.MakeModulesRequest
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeProjectRequest
import com.specificlanguages.mops.protocol.MakeResponse
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for how [DomainRequestHandler] routes make requests. The MPS operations are mocked, so no MPS boot is
 * triggered.
 */
class MakeRequestHandlerTest {
    private val operations = mock<MpsWrite>()
    private val extra = mock<MpsExtra>()
    private val workspacePath = Path.of("/workspace/example")
    private val handler = DomainRequestHandler(workspacePath, mpsAccessOver(operations, extra))

    @Test
    fun `make-modules makes the requested modules and returns the result directly`() {
        val expected = MakeResponse(MakeOutcome.SUCCESS, moduleCount = 2, messages = emptyList())
        whenever(extra.makeModules(listOf("moduleA", "moduleB"), MakeGenerationOptions())).thenReturn(expected)

        val response = handler.handleDomainRequest(MakeModulesRequest(TOKEN, listOf("moduleA", "moduleB")))

        assertEquals(expected, response)
        verify(extra).makeModules(listOf("moduleA", "moduleB"), MakeGenerationOptions())
        verifyNoInteractions(operations)
    }

    @Test
    fun `make-project makes the whole project and returns the result directly`() {
        val expected = MakeResponse(
            MakeOutcome.FAILED,
            moduleCount = 5,
            messages = listOf(MakeMessageJson(MakeMessageKind.ERROR, "boom")),
        )
        whenever(extra.makeProject(MakeGenerationOptions())).thenReturn(expected)

        val response = handler.handleDomainRequest(MakeProjectRequest(TOKEN))

        assertEquals(expected, response)
        verify(extra).makeProject(MakeGenerationOptions())
        verifyNoInteractions(operations)
    }

    @Test
    fun `make requests pass their generation options to the make`() {
        val expected = MakeResponse(MakeOutcome.SUCCESS, moduleCount = 1, messages = emptyList())
        val modulesOptions = MakeGenerationOptions(threads = 3, strict = true, messages = MakeMessageLevel.INFO)
        val projectOptions = MakeGenerationOptions(
            saveTransientModels = true,
            performanceTracing = PerformanceTracingLevel.STEPS,
        )
        whenever(extra.makeModules(listOf("moduleA"), modulesOptions)).thenReturn(expected)
        whenever(extra.makeProject(projectOptions)).thenReturn(expected)

        assertEquals(
            expected,
            handler.handleDomainRequest(MakeModulesRequest(TOKEN, listOf("moduleA"), generation = modulesOptions)),
        )
        assertEquals(expected, handler.handleDomainRequest(MakeProjectRequest(TOKEN, generation = projectOptions)))
    }

    @Test
    fun `a request exception on the make path maps to its error code`() {
        whenever(extra.makeModules(listOf("missing"), MakeGenerationOptions()))
            .thenThrow(MpsRequestException(MpsErrorCode.TARGET_NOT_FOUND, "module not found: missing"))

        val response = handler.handleDomainRequest(MakeModulesRequest(TOKEN, listOf("missing")))

        assertEquals(errorResponse("TARGET_NOT_FOUND", "module not found: missing"), response)
    }


    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)

    private companion object {
        const val TOKEN = "test-token"
    }
}
