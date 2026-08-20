package com.specificlanguages.mops.daemon

import jetbrains.mps.project.*
import jetbrains.mps.smodel.*
import org.jetbrains.mps.openapi.module.*
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

sealed interface ModuleHandle {
    val moduleReference: String; val moduleName: String; val kind: String; val descriptorPath: String
    val facets: List<SModuleFacet>; val sModule: SModule
}
abstract class AbstractModuleHandle(final override val sModule: SModule, final override val kind: String) : ModuleHandle {
    override val moduleReference get() = PersistenceFacade.getInstance().asString(sModule.moduleReference)
    override val moduleName get() = requireNotNull(sModule.moduleName)
    override val descriptorPath get() = requireNotNull((sModule as AbstractModule).descriptorFile).path
    override val facets get() = (sModule as AbstractModule).facets.toList()
}
class LanguageHandle(language: Language) : AbstractModuleHandle(language, "language") {
    val generators get() = (sModule as Language).generators.map(::GeneratorHandle)
}
class SolutionHandle(solution: Solution) : AbstractModuleHandle(solution, "solution")
class DevkitHandle(devkit: DevKit) : AbstractModuleHandle(devkit, "devkit")
class GeneratorHandle(generator: Generator) : AbstractModuleHandle(generator, "generator") {
    val alias get() = (sModule as Generator).moduleDescriptor.alias
    val sourceLanguage get() = LanguageHandle(requireNotNull((sModule as Generator).sourceLanguage))
}
internal fun moduleHandle(module: SModule): ModuleHandle = when (module) {
    is Language -> LanguageHandle(module); is Solution -> SolutionHandle(module); is DevKit -> DevkitHandle(module)
    is Generator -> GeneratorHandle(module); else -> error("unsupported MPS module kind ${module.javaClass.name}")
}
