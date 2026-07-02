package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsResult
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.*
import java.nio.file.Path
import kotlin.io.path.pathString

private sealed interface DaemonErrorCode {
    val name: String

    object UnsupportedRequest : DaemonErrorCode {
        override val name = "UNSUPPORTED_REQUEST"
    }
    object GenericFailure : DaemonErrorCode {
        override val name = "GENERIC_FAILURE"
    }
    class MpsError(code: MpsErrorCode) : DaemonErrorCode {
        override val name = code.name
    }
    class ProtocolError(override val name: String) : DaemonErrorCode
}

class DomainRequestHandler(val workspacePath: Path, val mpsAccess: MpsAccess) {
    fun handleDomainRequest(request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> readResponse({
                getNode(request.target)
            }) { ModelGetNodeResponse(node = it) }

            is FindUsagesRequest -> readResponse({
                findUsages(request.target, request.limit)
            }) {
                FindUsagesResponse(limit = it.limit, truncated = it.truncated, usages = it.usages)
            }

            is FindInstancesRequest -> readResponse({
                findInstances(request.concept, request.exact, request.limit)
            }) {
                FindInstancesResponse(limit = it.limit, truncated = it.truncated, nodes = it.nodes)
            }

            is ModelEditRequest -> writeResponse({
                modelEdit(request.batch)
            }) { it }

            is ModelResaveRequest -> writeResponse({
                resave(request.modelTarget)
            }) { ModelResaveResponse(modelTarget = request.modelTarget) }

            is MpsListRequest -> mpsAccess.read {
                list(target = request.target, depth = request.depth)
            }.toResponse { MpsListResponse(root = it) }

            else -> errorResponse(DaemonErrorCode.UnsupportedRequest, "unsupported request type: ${request.type}")
        }
    }

    private fun <T> readResponse(
        action: MpsRead.() -> MpsResult<T>,
        success: (T) -> DaemonResponse,
    ): DaemonResponse =
        try {
            mpsAccess.read(action).toResponse(success)
        } catch (exception: Exception) {
            errorResponse(DaemonErrorCode.GenericFailure, exception.message ?: exception.javaClass.name)
        }

    private fun <T> writeResponse(
        action: MpsWrite.() -> MpsResult<T>,
        success: (T) -> DaemonResponse,
    ): DaemonResponse =
        try {
            mpsAccess.write(action).toResponse(success)
        } catch (throwable: Throwable) {
            errorResponse(DaemonErrorCode.GenericFailure, throwable.message ?: throwable.javaClass.name)
        }

    private fun <T> MpsResult<T>.toResponse(success: (T) -> DaemonResponse): DaemonResponse =
        when (this) {
            is MpsResult.Ok -> success(value)
            is MpsResult.Error -> errorResponse(DaemonErrorCode.MpsError(code), message)
            is MpsResult.ProtocolError -> errorResponse(DaemonErrorCode.ProtocolError(code), message)
        }

    private fun errorResponse(code: DaemonErrorCode, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code.name, message = message, workspacePath = workspacePath.pathString)
}
