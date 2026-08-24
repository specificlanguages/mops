package com.specificlanguages.mops.daemon

import jetbrains.mps.project.*
import jetbrains.mps.smodel.*
import org.jetbrains.mps.openapi.module.*
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

sealed interface ModuleHandle {
    val moduleReference: String; val moduleName: String; val kind: String; val descriptorPath: String
    val facets: List<SModuleFacet>; val sModule: SModule
    fun createModel(modelName: String): ModelHandle
    fun createModel(modelName: String, filePerRoot: Boolean): ModelHandle
}
abstract class AbstractModuleHandle(
    final override val sModule: SModule,
    final override val kind: String,
    protected val context: CodeAccessContext? = null,
    protected val project: Project? = null,
) : ModuleHandle {
    override val moduleReference get() = PersistenceFacade.getInstance().asString(sModule.moduleReference)
    override val moduleName get() = requireNotNull(sModule.moduleName)
    override val descriptorPath get() = requireNotNull((sModule as AbstractModule).descriptorFile).path
    override val facets get() = (sModule as AbstractModule).facets.toList()
    override fun createModel(modelName: String): ModelHandle = createModel(modelName, false)
    override fun createModel(modelName: String, filePerRoot: Boolean): ModelHandle {
        val active = requireNotNull(context) { "this Module Handle is not attached to a Code Mode project session" }
        check(active.block == "edit") { "createModel requires an edit access block" }
        val owner = requireNotNull(project)
        check(owner.isProjectModule(sModule)) { "Module Handle belongs to a different MPS Project session" }
        val model = ModelCreator(owner).create(sModule, modelName, filePerRoot)
        return ModelHandle(model, this)
    }
}
class LanguageHandle(language: Language, context: CodeAccessContext? = null, project: Project? = null) :
    AbstractModuleHandle(language, "language", context, project) {
    val generators get() = (sModule as Language).generators.map { GeneratorHandle(it, context, project) }
}
class SolutionHandle(solution: Solution, context: CodeAccessContext? = null, project: Project? = null) : AbstractModuleHandle(solution, "solution", context, project)
class DevkitHandle(devkit: DevKit, context: CodeAccessContext? = null, project: Project? = null) : AbstractModuleHandle(devkit, "devkit", context, project)
class GeneratorHandle(generator: Generator, context: CodeAccessContext? = null, project: Project? = null) : AbstractModuleHandle(generator, "generator", context, project) {
    val alias get() = (sModule as Generator).moduleDescriptor.alias
    val sourceLanguage get() = LanguageHandle(requireNotNull((sModule as Generator).sourceLanguage), context, project)
}
internal fun moduleHandle(module: SModule, context: CodeAccessContext? = null, project: Project? = null): ModuleHandle = when (module) {
    is Language -> LanguageHandle(module, context, project); is Solution -> SolutionHandle(module, context, project)
    is DevKit -> DevkitHandle(module, context, project); is Generator -> GeneratorHandle(module, context, project)
    else -> error("unsupported MPS module kind ${module.javaClass.name}")
}
