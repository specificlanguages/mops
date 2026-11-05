package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.*
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Admits each domain request to the project and runs it. See docs/daemon-request-concurrency.md for how admission
 * batches parallel readers between refreshes of external changes.
 */
class DomainRequestHandler(val workspacePath: Path, val mpsAccess: MpsAccess) {
    private val gate = ProjectRequestGate { mpsAccess.extra { refreshExternalChanges() } }

    fun handleDomainRequest(request: DomainRequest): DaemonResponse =
        try {
            val route = route(request)
            gate.run(route.admission == Admission.PARALLEL_READ) {
                route.run().also {
                    if (route.admission == Admission.EXCLUSIVE_SAVING) mpsAccess.extra { saveProject() }
                }
            }
        } catch (exception: MpsRequestException) {
            errorResponse(exception.code.name, exception.message)
        } catch (throwable: Throwable) {
            errorResponse(MpsErrorCode.GENERIC_FAILURE.name, throwable.message ?: throwable.javaClass.name)
        }

    private enum class Admission {
        /** Runs alongside other parallel readers admitted after the same refresh. Never saves the project. */
        PARALLEL_READ,

        /** Runs alone. Model checks and rendering use shared checker, typesystem, or EDT services. */
        EXCLUSIVE,

        /** Runs alone and saves the project afterwards, because it may modify it. */
        EXCLUSIVE_SAVING,
    }

    private class Route(val admission: Admission, val run: () -> DaemonResponse)

    private fun parallelRead(run: () -> DaemonResponse) = Route(Admission.PARALLEL_READ, run)

    private fun exclusive(run: () -> DaemonResponse) = Route(Admission.EXCLUSIVE, run)

    private fun exclusiveSaving(run: () -> DaemonResponse) = Route(Admission.EXCLUSIVE_SAVING, run)

    // A dry run only plans the creation, so it leaves nothing to save.
    private fun creation(dryRun: Boolean, run: () -> DaemonResponse) = if (dryRun) exclusive(run) else exclusiveSaving(run)

    // One branch per domain request: each states how the request is admitted and what it runs.
    @Suppress("LongMethod")
    private fun route(request: DomainRequest): Route =
        when (request) {
            is ModelGetNodeRequest -> parallelRead {
                ModelGetNodeResponse(node = mpsAccess.read { getNode(request.target, request.ancestry, request.depth) })
            }

            is ModelGetNodeSummaryRequest -> parallelRead {
                ModelGetNodeSummaryResponse(node = mpsAccess.read { getNodeSummary(request.target, request.ancestry) })
            }

            is GetModuleRequest -> parallelRead { GetModuleResponse(module = mpsAccess.read { getModule(request.module) }) }

            is MpsListRequest -> parallelRead {
                MpsListResponse(
                    root = mpsAccess.read {
                        list(
                            target = request.target,
                            depth = request.depth,
                            limit = request.limit,
                            summary = request.summary,
                            role = request.role,
                        )
                    },
                )
            }

            is FindUsagesRequest -> parallelRead {
                mpsAccess.read { findUsages(request.target, resolveScope(request.scope), request.limit) }
            }

            is FindSubconceptsRequest -> parallelRead {
                mpsAccess.read { findSubconcepts(request.concept, request.concreteOnly, request.direct) }
            }

            is FindInstancesRequest -> parallelRead {
                mpsAccess.read {
                    findInstances(request.concept, request.exact, resolveScope(request.scope), request.filters, request.limit)
                }
            }

            is FindByNameRequest -> parallelRead {
                mpsAccess.read { findByName(request.pattern, resolveScope(request.scope), request.limit) }
            }

            is FindNodeByIdRequest -> parallelRead {
                mpsAccess.read { findNodeById(request.nodeId, resolveScope(request.scope), request.limit) }
            }

            is DiagnoseModulesRequest -> parallelRead { mpsAccess.read { diagnoseModules() } }

            is DiagnoseModuleRequest -> parallelRead { mpsAccess.read { diagnoseModule(request.module) } }

            is CodeCatalogRequest -> parallelRead { CodeCatalog.response(request) }

            is ModelRenderNodeRequest -> exclusive {
                ModelRenderNodeResponse(text = mpsAccess.extra { renderNode(request.target, request.allowReflective) })
            }

            is ModelCheckRequest -> exclusive { mpsAccess.read { checkModel(request.target, request.limit) } }

            is ProjectCheckRequest -> exclusive { mpsAccess.read { checkProject(request.limit) } }

            is ModuleCheckRequest -> exclusive { mpsAccess.read { checkModules(request.modules, request.limit) } }

            is ModelEditRequest -> exclusiveSaving {
                mpsAccess.write { modelEdit(request.batch, request.constraints, request.ignoreMissingLanguages) }
            }

            is MakeModulesRequest -> exclusiveSaving { mpsAccess.extra { makeModules(request.modules) } }

            is MakeProjectRequest -> exclusiveSaving { mpsAccess.extra { makeProject() } }

            is TestRunRequest -> exclusiveSaving { TestRunResponse(mpsAccess.extra { runTests(request) }) }

            is CodeRunRequest -> exclusiveSaving { mpsAccess.extra { runCode(request) } }

            is CreateModuleRequest -> creation(request.dryRun) { mpsAccess.write { createModule(request) } }

            is CreateModelRequest -> creation(request.dryRun) { mpsAccess.write { createModel(request) } }
        }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)
}
