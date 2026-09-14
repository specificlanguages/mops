package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsAccess
import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.core.platform.Platform
import java.nio.file.Path
import kotlin.io.path.pathString

class DomainRequestHandler(val workspacePath: Path, val mpsAccess: MpsAccess, private val platform: Platform? = null) {
    fun handleDomainRequest(request: DaemonRequest): DaemonResponse =
        try {
            mpsAccess.extra { refreshExternalChanges() }
            when (request) {
                is ModelGetNodeRequest ->
                    ModelGetNodeResponse(node = mpsAccess.read { getNode(request.target, request.ancestry) })

                is ModelRenderNodeRequest -> ModelRenderNodeResponse(
                    text = mpsAccess.extra { renderNode(request.target, request.allowReflective) },
                )

                is ModelCheckRequest -> mpsAccess.read { checkModel(request.target, request.limit) }

                is FindUsagesRequest -> mpsAccess.read {
                    findUsages(request.target, resolveScope(request.scope), request.limit)
                }

                is FindInstancesRequest -> mpsAccess.read {
                    findInstances(
                        request.concept,
                        request.exact,
                        resolveScope(request.scope),
                        request.filters,
                        request.limit,
                    )
                }

                is FindByNameRequest -> mpsAccess.read {
                    findByName(request.pattern, resolveScope(request.scope), request.limit)
                }

                is FindNodeByIdRequest -> mpsAccess.read {
                    findNodeById(request.nodeId, resolveScope(request.scope), request.limit)
                }

                is ModelEditRequest -> mpsAccess.write { modelEdit(request.batch, request.constraints) }

                is MpsListRequest -> MpsListResponse(
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

                is DiagnoseModulesRequest -> mpsAccess.read { diagnoseModules() }

                is DiagnoseModuleRequest -> mpsAccess.read { diagnoseModule(request.module) }

                is MakeModulesRequest -> mpsAccess.extra { makeModules(request.modules) }

                is MakeProjectRequest -> mpsAccess.extra { makeProject() }

                is CodeRunRequest -> {
                    val access = mpsAccess as? JetBrainsMpsAccess
                        ?: error("Running code mode requires the JetBrains MPS runtime")
                    CodeModeExecutor(access, access.project, requireNotNull(platform) { "Running code mode requires the MPS platform" })
                        .execute(request)
                }

                is CodeCatalogRequest -> CodeCatalog.response(request)

                is CreateLanguageRequest -> moduleResponse(request.dryRun) { createLanguage(request) }
                is CreateSolutionRequest -> moduleResponse(request.dryRun) { createSolution(request) }
                is CreateDevkitRequest -> moduleResponse(request.dryRun) { createDevkit(request) }
                is CreateGeneratorRequest -> moduleResponse(request.dryRun) { createGenerator(request) }
                is CreateModelRequest -> mpsAccess.write { ModelCreator((mpsAccess as JetBrainsMpsAccess).project).create(request) }

                else -> errorResponse("UNSUPPORTED_REQUEST", "unsupported request type: ${request::class.simpleName}")
            }.also { mpsAccess.extra { saveProject() } }
        } catch (exception: MpsRequestException) {
            errorResponse(exception.code.name, exception.message)
        } catch (throwable: Throwable) {
            errorResponse(MpsErrorCode.GENERIC_FAILURE.name, throwable.message ?: throwable.javaClass.name)
        }

    private fun errorResponse(code: String, message: String): DaemonErrorResponse =
        DaemonErrorResponse(errorCode = code, message = message, workspacePath = workspacePath.pathString)

    private fun moduleResponse(dryRun: Boolean, operation: ModuleCreationCliAdapter.() -> ModuleCreationResponse): ModuleCreationResponse {
        val project = (mpsAccess as JetBrainsMpsAccess).project
        return mpsAccess.write {
            ModuleCreationCliAdapter(ModuleCreator(project)).operation().also {
                if (!dryRun) project.repository.saveAll()
            }
        }
    }
}
