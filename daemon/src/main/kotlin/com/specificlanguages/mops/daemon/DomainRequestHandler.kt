package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsRead
import com.specificlanguages.mops.daemon.core.MpsResult
import com.specificlanguages.mops.daemon.core.MpsWrite
import com.specificlanguages.mops.protocol.*
import java.nio.file.Path
import kotlin.io.path.pathString

class DomainRequestHandler(val workspacePath: Path, val mpsAccess: MpsAccess) {
    fun handleDomainRequest(request: DaemonRequest): DaemonResponse {
        return when (request) {
            is ModelGetNodeRequest -> readResponse(mpsAccess, failureCode = "GET_NODE_FAILED", {
                getNode(target = request.target)
            }) { ModelGetNodeResponse(node = it) }

            is FindUsagesRequest -> readResponse(mpsAccess, failureCode = "FIND_USAGES_FAILED", {
                findUsages(target = request.target, limit = request.limit)
            }) {
                FindUsagesResponse(limit = it.limit, truncated = it.truncated, usages = it.usages)
            }

            is FindInstancesRequest -> readResponse(mpsAccess, failureCode = "FIND_INSTANCES_FAILED", {
                findInstances(concept = request.concept, exact = request.exact, limit = request.limit)
            }) {
                FindInstancesResponse(limit = it.limit, truncated = it.truncated, nodes = it.nodes)
            }

            is EditApplyRequest -> writeResponse(mpsAccess, failureCode = "EDIT_APPLY_FAILED", {
                applyEdit(request.batch)
            }) { it }

            is ModelResaveRequest -> writeResponse(mpsAccess, failureCode = "SAVE_FAILED", {
                resave(request.modelTarget)
            }) { ModelResaveResponse(modelTarget = request.modelTarget) }

            is MpsListRequest -> mpsAccess.read {
                list(target = request.target, depth = request.depth)
            }.toResponse { MpsListResponse(root = it) }

            else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request.type}")
        }
    }

    private fun <T> readResponse(
        mpsAccess: MpsAccess,
        failureCode: String,
        action: MpsRead.() -> MpsResult<T>,
        success: (T) -> DaemonResponse,
    ): DaemonResponse =
        try {
            mpsAccess.read(action).toResponse(success)
        } catch (exception: Exception) {
            errorResponse(failureCode, exception.message ?: exception.javaClass.name)
        }

    private fun <T> writeResponse(
        mpsAccess: MpsAccess,
        failureCode: String,
        action: MpsWrite.() -> MpsResult<T>,
        success: (T) -> DaemonResponse,
    ): DaemonResponse =
        try {
            mpsAccess.write(action).toResponse(success)
        } catch (throwable: Throwable) {
            errorResponse(failureCode, throwable.message ?: throwable.javaClass.name)
        }

    private fun <T> MpsResult<T>.toResponse(success: (T) -> DaemonResponse): DaemonResponse =
        when (this) {
            is MpsResult.Ok -> success(value)
            is MpsResult.Error -> errorResponse(code.protocolCode, message)
            is MpsResult.ProtocolError -> errorResponse(code, message)
        }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
