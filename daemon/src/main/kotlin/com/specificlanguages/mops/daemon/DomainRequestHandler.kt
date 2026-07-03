package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.*
import java.nio.file.Path
import kotlin.io.path.pathString

class DomainRequestHandler(val workspacePath: Path, val mpsAccess: MpsAccess) {
    fun handleDomainRequest(request: DaemonRequest): DaemonResponse =
        try {
            when (request) {
                is ModelGetNodeRequest -> ModelGetNodeResponse(node = mpsAccess.read { getNode(request.target) })

                is FindUsagesRequest -> mpsAccess.read { findUsages(request.target, request.limit) }

                is FindInstancesRequest -> mpsAccess.read {
                    findInstances(request.concept, request.exact, request.limit)
                }

                is ModelEditRequest -> mpsAccess.write { modelEdit(request.batch) }

                is ModelResaveRequest -> {
                    mpsAccess.write { resave(request.modelTarget) }
                    ModelResaveResponse(modelTarget = request.modelTarget)
                }

                is MpsListRequest -> MpsListResponse(
                    root = mpsAccess.read { list(target = request.target, depth = request.depth) },
                )

                else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request::class.simpleName}")
            }
        } catch (exception: MpsRequestException) {
            errorResponse(exception.code.name, exception.message)
        } catch (throwable: Throwable) {
            errorResponse(MpsErrorCode.GENERIC_FAILURE.name, throwable.message ?: throwable.javaClass.name)
        }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
