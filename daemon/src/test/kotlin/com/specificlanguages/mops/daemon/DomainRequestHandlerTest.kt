package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.daemon.core.ResolvedScope
import com.specificlanguages.mops.protocol.DaemonErrorResponse
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.FindByNameRequest
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesRequest
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesRequest
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.ModelEditRequest
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.ModelGetNodeRequest
import com.specificlanguages.mops.protocol.ModelGetNodeResponse
import com.specificlanguages.mops.protocol.ModelResaveRequest
import com.specificlanguages.mops.protocol.ModelResaveResponse
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsListRequest
import com.specificlanguages.mops.protocol.MpsListResponse
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget
import com.specificlanguages.mops.protocol.PingRequest
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the MPS-free request dispatch and error mapping in [DomainRequestHandler]. The MPS
 * operations are mocked, so no MPS boot is triggered and each test asserts exactly which operation the
 * handler routed the request to.
 */
class DomainRequestHandlerTest {
    private val operations = mock<MpsWrite>()
    private val workspacePath = Path.of("/workspace/example")
    private val handler = DomainRequestHandler(workspacePath, mpsAccessOver(operations))

    @Test
    fun `get-node reads and wraps the exported node`() {
        val node = MpsNodeJson(concept = "SomeConcept", id = "42")
        val target = NodeTarget.NodeReference("r:model/1")
        whenever(operations.getNode(target)).thenReturn(node)

        val response = handler.handleDomainRequest(ModelGetNodeRequest(TOKEN, target))

        assertEquals(ModelGetNodeResponse(node), response)
        verify(operations).getNode(target)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `find-instances resolves the scope then reads and returns the response directly`() {
        val expected = FindInstancesResponse(limit = 10, truncated = true, nodes = emptyList())
        whenever(operations.resolveScope(null)).thenReturn(ResolvedScope.EditableProjectSources)
        whenever(operations.findInstances("some.Concept", true, 10, ResolvedScope.EditableProjectSources))
            .thenReturn(expected)

        val response = handler.handleDomainRequest(
            FindInstancesRequest(TOKEN, concept = "some.Concept", exact = true, limit = 10),
        )

        assertEquals(expected, response)
        verify(operations).resolveScope(null)
        verify(operations).findInstances("some.Concept", true, 10, ResolvedScope.EditableProjectSources)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `find requests resolve the scope segments and forward the resolved scope`() {
        val segments = listOf("com.example", ".model")
        val moduleScope = ResolvedScope.Module("ref(com.example)")
        whenever(operations.resolveScope(segments)).thenReturn(moduleScope)
        whenever(operations.findInstances("some.Concept", false, 10, moduleScope))
            .thenReturn(FindInstancesResponse(limit = 10, truncated = false, nodes = emptyList()))

        handler.handleDomainRequest(
            FindInstancesRequest(TOKEN, concept = "some.Concept", exact = false, limit = 10, scope = segments),
        )

        verify(operations).resolveScope(segments)
        verify(operations).findInstances("some.Concept", false, 10, moduleScope)

        val target = NodeTarget.InModel("some.model", "7")
        whenever(operations.resolveScope(listOf("/"))).thenReturn(ResolvedScope.Repository)
        whenever(operations.findUsages(target, 5, ResolvedScope.Repository))
            .thenReturn(FindUsagesResponse(limit = 5, truncated = false, usages = emptyList()))

        handler.handleDomainRequest(FindUsagesRequest(TOKEN, target, limit = 5, scope = listOf("/")))

        verify(operations).resolveScope(listOf("/"))
        verify(operations).findUsages(target, 5, ResolvedScope.Repository)
    }

    @Test
    fun `find-by-name reads and returns the response directly`() {
        val expected = FindByNameResponse(limit = 10, truncated = false, nodes = emptyList())
        whenever(operations.findByName("Json", 10, true)).thenReturn(expected)

        val response = handler.handleDomainRequest(
            FindByNameRequest(TOKEN, pattern = "Json", limit = 10, all = true),
        )

        assertEquals(expected, response)
        verify(operations).findByName("Json", 10, true)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `find-usages resolves the scope then reads and returns the response directly`() {
        val expected = FindUsagesResponse(limit = 5, truncated = false, usages = emptyList())
        val target = NodeTarget.InModel("some.model", "7")
        whenever(operations.resolveScope(null)).thenReturn(ResolvedScope.EditableProjectSources)
        whenever(operations.findUsages(target, 5, ResolvedScope.EditableProjectSources)).thenReturn(expected)

        val response = handler.handleDomainRequest(FindUsagesRequest(TOKEN, target, limit = 5))

        assertEquals(expected, response)
        verify(operations).resolveScope(null)
        verify(operations).findUsages(target, 5, ResolvedScope.EditableProjectSources)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `list reads and wraps the tree`() {
        val root = MpsListEntryJson(type = "project", name = "example")
        whenever(operations.list(listOf("moduleA"), 3)).thenReturn(root)

        val response = handler.handleDomainRequest(
            MpsListRequest(TOKEN, target = listOf("moduleA"), depth = 3),
        )

        assertEquals(MpsListResponse(root), response)
        verify(operations).list(listOf("moduleA"), 3)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `model-edit writes and returns the response directly`() {
        val expected = ModelEditResponse(created = mapOf("\$a" to "r:model/9"), violations = emptyList())
        val batch = EditBatch(operations = emptyList())
        whenever(operations.modelEdit(batch)).thenReturn(expected)

        val response = handler.handleDomainRequest(ModelEditRequest(TOKEN, batch))

        assertEquals(expected, response)
        verify(operations).modelEdit(batch)
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `model-resave writes and wraps the target`() {
        val response = handler.handleDomainRequest(ModelResaveRequest(TOKEN, modelTarget = "some.model"))

        assertEquals(ModelResaveResponse(modelTarget = "some.model"), response)
        verify(operations).resave("some.model")
        verifyNoMoreInteractions(operations)
    }

    @Test
    fun `unsupported request type yields an error response without touching MPS`() {
        val response = handler.handleDomainRequest(PingRequest(TOKEN))

        assertEquals(errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: PingRequest"), response)
        verifyNoInteractions(operations)
    }

    @Test
    fun `a request exception on a read path maps to its error code`() {
        whenever(operations.resolveScope(null)).thenReturn(ResolvedScope.EditableProjectSources)
        whenever(operations.findInstances("some.Concept", false, 100, ResolvedScope.EditableProjectSources))
            .thenThrow(MpsRequestException(MpsErrorCode.CONCEPT_NOT_FOUND, "concept not found: some.Concept"))

        val response = handler.handleDomainRequest(
            FindInstancesRequest(TOKEN, concept = "some.Concept", exact = false, limit = 100),
        )

        assertEquals(errorResponse("CONCEPT_NOT_FOUND", "concept not found: some.Concept"), response)
    }

    @Test
    fun `a request exception on a write path maps to its error code`() {
        doThrow(MpsRequestException(MpsErrorCode.MODEL_NOT_FOUND, "model not found: some.model"))
            .whenever(operations).resave("some.model")

        val response = handler.handleDomainRequest(ModelResaveRequest(TOKEN, modelTarget = "some.model"))

        assertEquals(errorResponse("MODEL_NOT_FOUND", "model not found: some.model"), response)
    }

    @Test
    fun `an unexpected failure maps to a generic failure carrying its message`() {
        val target = NodeTarget.NodeReference("r:model/1")
        whenever(operations.getNode(target)).thenThrow(IllegalStateException("boom"))

        val response = handler.handleDomainRequest(ModelGetNodeRequest(TOKEN, target))

        assertEquals(errorResponse("GENERIC_FAILURE", "boom"), response)
    }

    @Test
    fun `an unexpected failure without a message falls back to the exception class name`() {
        val target = NodeTarget.NodeReference("r:model/1")
        whenever(operations.getNode(target)).thenThrow(RuntimeException())

        val response = handler.handleDomainRequest(ModelGetNodeRequest(TOKEN, target))

        assertEquals(errorResponse("GENERIC_FAILURE", RuntimeException::class.java.name), response)
    }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)

    private companion object {
        const val TOKEN = "test-token"
    }
}
