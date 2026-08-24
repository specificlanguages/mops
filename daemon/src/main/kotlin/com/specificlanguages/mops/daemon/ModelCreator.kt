package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.*
import jetbrains.mps.extapi.model.SModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.extapi.persistence.FolderDataSource
import jetbrains.mps.extapi.persistence.SourceRootKinds
import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryRuleService
import jetbrains.mps.extapi.persistence.datasource.PreinstalledDataSourceTypes
import jetbrains.mps.persistence.*
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.exists

class ModelCreator(private val project: Project) {
    private val persistence = PersistenceFacade.getInstance()

    fun create(request: CreateModelRequest): ModelCreationResponse {
        val module = resolveProjectModule(request.module)
        val actualName = expandName(request.modelName, requireNotNull(module.moduleName))
        val format = if (request.filePerRoot) ModelPersistence.FILE_PER_ROOT else ModelPersistence.SINGLE_FILE
        val modelName = SModelName(actualName)
        val observations = module.modelRoots.mapIndexed { index, root -> inspect(index, root, modelName, format) }
        val candidates = observations.filter { it.problem == null }
        if (candidates.isEmpty()) fail("no model root can create $actualName in ${module.moduleName}: " +
            observations.joinToString("; ") { it.diagnostic() })
        if (candidates.size > 1) fail("more than one model root can create $actualName in ${module.moduleName}: " +
            candidates.joinToString("; ") { it.diagnostic() })
        val selected = candidates.single()
        val plan = ModelCreationPlan(actualName, requireNotNull(module.moduleName), persistence.asString(module.moduleReference),
            format, selected.location!!)
        if (request.dryRun) return ModelCreationResponse(plan = plan)

        val target = runCatching { Path.of(selected.location) }.getOrNull()
        val absentBefore = target?.exists() == false
        val model = selected.root!!.createModel(modelName, null, dataSourceType(format), factoryType(format))
        try {
            (model as EditableSModel).save()
        } catch (saveFailure: Throwable) {
            val cleanupFailures = mutableListOf<String>()
            runCatching { (module as SModuleBase).unregisterModel(model as SModelBase) }
                .exceptionOrNull()?.let { cleanupFailures += "could not detach model: ${it.message}" }
            if (absentBefore && target != null && target.exists()) {
                runCatching { deleteCreatedTarget(target) }.exceptionOrNull()
                    ?.let { cleanupFailures += "could not remove created artifacts: ${it.message}" }
            }
            val residue = if (target?.exists() == true) "; possible residue at $target" else ""
            val cleanup = cleanupFailures.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "; ") ?: ""
            throw IllegalStateException("initial save failed for $actualName at ${selected.location}: " +
                "${saveFailure.message ?: saveFailure.javaClass.name}$residue$cleanup", saveFailure)
        }
        return ModelCreationResponse(report = ModelCreationReport(actualName, persistence.asString(model.reference),
            requireNotNull(module.moduleName), persistence.asString(module.moduleReference), format, selected.location))
    }

    fun create(module: SModule, modelName: String, filePerRoot: Boolean): SModel {
        val response = create(CreateModelRequest("", modelName, persistence.asString(module.moduleReference), filePerRoot))
        val reference = requireNotNull(response.report).modelReference
        return persistence.createModelReference(reference).resolve(project.repository)
            ?: error("created model is no longer registered: $reference")
    }

    private fun inspect(index: Int, root: org.jetbrains.mps.openapi.persistence.ModelRoot, name: SModelName,
                        format: ModelPersistence): RootObservation {
        if (root !is DefaultModelRoot) return RootObservation(index, root.presentation, root.type, problem = "unsupported model root")
        if (!root.canCreateModels()) return RootObservation(index, root.presentation, root.type, problem = "model creation is disabled")
        return try {
            val sourceRoot = root.getSourceRoots(SourceRootKinds.SOURCES).firstOrNull()
                ?: return RootObservation(index, root.presentation, root.type, problem = "no SOURCES source root")
            val bridge = DataSourceFactoryBridge(root, requireNotNull(project.platform.findComponent(DataSourceFactoryRuleService::class.java)))
            val prepared = bridge.create(name, sourceRoot, dataSourceType(format))
            val factory = PersistenceRegistry.getInstance().getModelFactory(factoryType(format))
                ?: return RootObservation(index, root.presentation, root.type, problem = "model factory is unavailable")
            val problem = factory.canCreate(prepared.dataSource, name, *prepared.options.convertToLoadingOptions())
            RootObservation(index, root.presentation, root.type, root, physicalLocation(prepared.dataSource),
                problem.takeUnless { it === org.jetbrains.mps.openapi.persistence.MFProblem.NO_PROBLEM }?.description)
        } catch (failure: Throwable) {
            RootObservation(index, root.presentation, root.type, problem = failure.message ?: failure.javaClass.simpleName)
        }
    }

    private fun resolveProjectModule(target: String): SModule {
        val matches = project.repository.modules.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }
        if (matches.size != 1) fail(if (matches.isEmpty()) "module not found: $target" else "ambiguous module: $target")
        return matches.single().also { if (!project.isProjectModule(it)) fail("module is not a project module: $target") }
    }

    private fun expandName(requested: String, moduleName: String): String = when {
        requested == "." || requested.startsWith("..") -> fail("invalid relative model name: $requested")
        requested.startsWith('.') -> moduleName + requested
        requested.isBlank() -> fail("model name must not be empty")
        else -> requested
    }

    private fun dataSourceType(format: ModelPersistence) = when (format) {
        ModelPersistence.SINGLE_FILE -> PreinstalledDataSourceTypes.MPS
        ModelPersistence.FILE_PER_ROOT -> PreinstalledDataSourceTypes.MODEL
    }

    private fun physicalLocation(dataSource: org.jetbrains.mps.openapi.persistence.DataSource): String = when (dataSource) {
        is FileDataSource -> dataSource.file.path
        is FolderDataSource -> dataSource.folder.path
        else -> dataSource.location
    }

    private fun factoryType(format: ModelPersistence) = when (format) {
        ModelPersistence.SINGLE_FILE -> PreinstalledModelFactoryTypes.PLAIN_XML
        ModelPersistence.FILE_PER_ROOT -> PreinstalledModelFactoryTypes.PER_ROOT_XML
    }

    private fun deleteCreatedTarget(target: Path) {
        if (Files.isDirectory(target)) Files.walk(target).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        else Files.deleteIfExists(target)
    }

    private fun fail(message: String): Nothing = throw MpsRequestException(MpsErrorCode.INVALID_REQUEST, message)

    private data class RootObservation(
        val index: Int, val presentation: String, val type: String, val root: DefaultModelRoot? = null,
        val location: String? = null, val problem: String? = null,
    ) {
        fun diagnostic() = "root #${index + 1} '$presentation' ($type)" +
            (location?.let { " at $it" } ?: "") + (problem?.let { ": $it" } ?: "")
    }
}
