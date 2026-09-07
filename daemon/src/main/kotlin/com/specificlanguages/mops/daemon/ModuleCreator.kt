package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.FacetMementoJson
import com.specificlanguages.mops.protocol.GeneratorPersistence
import com.specificlanguages.mops.protocol.ModuleCreationPlan
import com.specificlanguages.mops.protocol.ModuleCreationPlanEntry
import com.specificlanguages.mops.protocol.ModuleKind
import com.specificlanguages.mops.protocol.SolutionUsagePreset
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.MementoImpl
import jetbrains.mps.project.*
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.facets.JavaModuleFacetImpl
import jetbrains.mps.project.modules.LanguageProducer
import jetbrains.mps.project.structure.modules.*
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.ModuleRepositoryFacade
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class ModuleCreator(private val project: Project) {
    private val mpsProject = project as? MPSProject ?: error("module creation requires an MPS file-based project")
    private val projectDirectory: Path = mpsProject.projectFile.toPath().let {
        when {
            it.resolve(".mps").isDirectory() -> it
            it.fileName.toString() == ".mps" -> it.parent
            else -> it.parent
        }
    }.toAbsolutePath().normalize()
    private val persistence = PersistenceFacade.getInstance()

    fun createLanguage(name: String, descriptorOverride: String? = null, withGenerator: Boolean = false): CreatedModules {
        val descriptor = descriptorPath(name, ModuleKind.LANGUAGE, descriptorOverride)
        preflight(name, descriptor, ".mpl")
        val generatorName = if (withGenerator) nextGeneratorName(name) else null
        val generatorDirectory = generatorName?.let { firstEmbeddedGeneratorDirectory(descriptor.parent) }
        prepareDescriptor(descriptor)
        val language = createLanguageModule(name, descriptor)
        val companions = generatorName?.let {
            val generator = createGeneratorModule(language, it, "main", false, descriptor, generatorDirectory!!)
            LanguageProducer.createTemplateModelIfNoneYet(mpsProject, generator)
            listOf(generator)
        } ?: emptyList()
        return CreatedModules(language, companions)
    }

    fun createSolution(name: String, descriptorOverride: String? = null,
                       preset: SolutionUsagePreset = SolutionUsagePreset.NOT_GENERATED): CreatedModules {
        val descriptor = descriptorPath(name, ModuleKind.SOLUTION, descriptorOverride)
        preflight(name, descriptor, ".msd")
        prepareDescriptor(descriptor)
        return CreatedModules(createSolutionModule(name, descriptor, preset))
    }

    fun createDevkit(name: String, descriptorOverride: String? = null): CreatedModules {
        val descriptor = descriptorPath(name, ModuleKind.DEVKIT, descriptorOverride)
        preflight(name, descriptor, ".devkit")
        prepareDescriptor(descriptor)
        val module = instantiate(DevkitDescriptor().apply { namespace = name; id = ModuleId.regular() }, descriptor) as DevKit
        mpsProject.addModule(module)
        module.setChanged()
        return CreatedModules(module)
    }

    fun createGenerator(languageTarget: String, alias: String, standalone: Boolean = false,
                        descriptorOverride: String? = null): CreatedModules {
        require(standalone || descriptorOverride == null) { "descriptor override is valid only for a standalone generator" }
        validateAlias(alias)
        val language = resolveLanguage(languageTarget)
        val name = nextGeneratorName(requireNotNull(language.moduleName))
        val languageDescriptor = requireNotNull((language as AbstractModule).descriptorFile).path.let(Path::of).toAbsolutePath().normalize()
        val descriptor = if (standalone) descriptorPath(name, ModuleKind.GENERATOR, descriptorOverride) else languageDescriptor
        if (standalone) preflight(name, descriptor, ".mpst") else validateNameFree(name)
        val generatorDirectory = if (standalone) descriptor.parent else firstEmbeddedGeneratorDirectory(languageDescriptor.parent)
        if (!standalone) validateEmptyTarget(generatorDirectory)
        generatorDirectory.createDirectories()
        if (standalone) Files.createFile(descriptor)
        return CreatedModules(createGeneratorModule(language, name, alias, standalone, descriptor, generatorDirectory))
    }

    fun planLanguage(name: String, descriptorOverride: String?, withGenerator: Boolean): ModuleCreationPlan {
        val descriptor = descriptorPath(name, ModuleKind.LANGUAGE, descriptorOverride)
        preflight(name, descriptor, ".mpl")
        val generatorName = if (withGenerator) nextGeneratorName(name) else null
        val generatorDirectory = generatorName?.let { firstEmbeddedGeneratorDirectory(descriptor.parent) }
        return ModuleCreationPlan(planEntry(name, ModuleKind.LANGUAGE, descriptor), generatorName?.let {
            listOf(planEntry(it, ModuleKind.GENERATOR, descriptor, alias = "main", persistence = GeneratorPersistence.EMBEDDED,
                generatorDirectory = generatorDirectory.toString()))
        } ?: emptyList())
    }

    fun planSolution(name: String, descriptorOverride: String?, preset: SolutionUsagePreset): ModuleCreationPlan {
        val descriptor = descriptorPath(name, ModuleKind.SOLUTION, descriptorOverride)
        preflight(name, descriptor, ".msd")
        return ModuleCreationPlan(planEntry(name, ModuleKind.SOLUTION, descriptor, facets = plannedFacets(preset)))
    }

    fun planDevkit(name: String, descriptorOverride: String?): ModuleCreationPlan {
        val descriptor = descriptorPath(name, ModuleKind.DEVKIT, descriptorOverride)
        preflight(name, descriptor, ".devkit")
        return ModuleCreationPlan(planEntry(name, ModuleKind.DEVKIT, descriptor))
    }

    fun planGenerator(languageTarget: String, alias: String, standalone: Boolean, descriptorOverride: String?): ModuleCreationPlan {
        require(standalone || descriptorOverride == null) { "descriptor override is valid only for a standalone generator" }
        validateAlias(alias)
        val language = resolveLanguage(languageTarget)
        val name = nextGeneratorName(requireNotNull(language.moduleName))
        val languageDescriptor = requireNotNull((language as AbstractModule).descriptorFile).path.let(Path::of).toAbsolutePath().normalize()
        val descriptor = if (standalone) descriptorPath(name, ModuleKind.GENERATOR, descriptorOverride) else languageDescriptor
        if (standalone) preflight(name, descriptor, ".mpst") else validateNameFree(name)
        val generatorDirectory = if (standalone) descriptor.parent else firstEmbeddedGeneratorDirectory(languageDescriptor.parent)
        if (!standalone) validateEmptyTarget(generatorDirectory)
        return ModuleCreationPlan(planEntry(name, ModuleKind.GENERATOR, descriptor, persistence.asString(language.moduleReference), alias,
            if (standalone) GeneratorPersistence.STANDALONE else GeneratorPersistence.EMBEDDED, generatorDirectory.toString()))
    }

    private fun createLanguageModule(name: String, descriptor: Path): Language {
        val dir = file(descriptor.parent)
        val descriptorFile = file(descriptor)
        val value = LanguageDescriptor().apply {
            namespace = name
            id = ModuleId.regular()
            modelRootDescriptors.add(DefaultModelRoot.createDescriptor(dir, dir.findChild("models")))
            moduleFacetDescriptors.add(JavaModuleFacetImpl.forJavaCodeModule(
                JavaModuleFacet.Compile.MPS, JavaModuleFacet.LoadClasses.ManagedByMPS, JavaModuleFacet.LoadExtensions.Plugin,
            ))
            outputRoot = "\${module}/source_gen"
        }
        return (ModuleRepositoryFacade(mpsProject).instantiate(value, descriptorFile) as Language)
            .also { mpsProject.addModule(it); it.setChanged() }
    }

    private fun createSolutionModule(name: String, descriptor: Path, preset: SolutionUsagePreset): Solution {
        val dir = file(descriptor.parent)
        val value = SolutionDescriptor().apply {
            namespace = name
            id = ModuleId.regular()
            modelRootDescriptors.add(DefaultModelRoot.createDescriptor(dir, dir.findChild("models")))
            when (preset) {
                SolutionUsagePreset.NOT_GENERATED -> Unit
                SolutionUsagePreset.TEXT -> moduleFacetDescriptors.add(ModuleFacetDescriptor("plaintext", MementoImpl().apply {
                    put("folders", "true")
                    put("root", "\${module}/source_gen")
                }))
                SolutionUsagePreset.JAVA -> moduleFacetDescriptors.add(JavaModuleFacetImpl.forNewJavaCodeModule())
                SolutionUsagePreset.JAVA_TESTS -> {
                    moduleFacetDescriptors.add(JavaModuleFacetImpl.forNewJavaCodeModule())
                    moduleFacetDescriptors.add(ModuleFacetDescriptor("tests", MementoImpl()))
                }
                SolutionUsagePreset.JAVA_MPS_PLUGIN -> moduleFacetDescriptors.add(JavaModuleFacetImpl.forJavaCodeModule(
                    JavaModuleFacet.Compile.MPS, JavaModuleFacet.LoadClasses.ManagedByMPS, JavaModuleFacet.LoadExtensions.Plugin,
                ))
            }
        }
        return (instantiate(value, file(descriptor)) as Solution).also { mpsProject.addModule(it); it.setChanged() }
    }

    private fun createGeneratorModule(language: Language, name: String, alias: String, standalone: Boolean,
                                      descriptor: Path, generatorDirectory: Path): Generator {
        val value = LanguageProducer.createGeneratorDescriptor(name, file(generatorDirectory), null).apply {
            setAlias(alias)
            setSourceLanguage(language.moduleReference)
            standaloneModule(standalone)
        }
        val descriptorFile = if (standalone) file(descriptor) else requireNotNull((language as AbstractModule).descriptorFile)
        val generator = if (standalone) {
            (ModuleRepositoryFacade(mpsProject).instantiate(value, descriptorFile) as Generator).also {
                mpsProject.addModule(it)
                (it as AbstractModule).setChanged()
            }
        } else {
            language.moduleDescriptor.generators.add(value)
            language.reloadAfterDescriptorChange()
            language.generators.single { it.moduleId == value.id }
        }
        return generator
    }

    private fun instantiate(descriptor: ModuleDescriptor, descriptorPath: Path): SModule = instantiate(descriptor, file(descriptorPath))
    private fun instantiate(descriptor: ModuleDescriptor, descriptorFile: jetbrains.mps.vfs.IFile): SModule =
        GeneralModuleFactory().instantiate(descriptor, descriptorFile)

    private fun descriptorPath(name: String, kind: ModuleKind, override: String?): Path {
        val conventional = when (kind) {
            ModuleKind.LANGUAGE -> "languages/$name/$name.mpl"
            ModuleKind.SOLUTION -> "solutions/$name/$name.msd"
            ModuleKind.DEVKIT -> "devkits/$name/$name.devkit"
            ModuleKind.GENERATOR -> "generators/$name/$name.mpst"
        }
        return (override?.let(Path::of) ?: Path.of(conventional)).let {
            if (it.isAbsolute) it else projectDirectory.resolve(it)
        }.normalize().toAbsolutePath()
    }

    private fun preflight(name: String, descriptor: Path, extension: String) {
        validateModuleName(name)
        validateNameFree(name)
        if (!descriptor.fileName.toString().endsWith(extension)) fail(MpsErrorCode.INVALID_DESCRIPTOR_PATH,
            "descriptor for $name must use $extension: $descriptor")
        if (descriptor.exists()) fail(MpsErrorCode.DESCRIPTOR_EXISTS, "descriptor already exists: $descriptor")
        validateEmptyTarget(descriptor.parent)
        val writableAncestor = generateSequence(descriptor.parent) { it.parent }.firstOrNull { it.exists() }
        if (writableAncestor == null || !Files.isWritable(writableAncestor)) fail(MpsErrorCode.PATH_NOT_WRITABLE, "path is not writable: $descriptor")
    }

    private fun validateEmptyTarget(directory: Path) {
        if (directory.exists() && (!directory.isDirectory() || Files.list(directory).use { it.findAny().isPresent }))
            fail(MpsErrorCode.TARGET_DIRECTORY_NOT_EMPTY, "target directory is not empty: $directory")
    }

    private fun validateModuleName(name: String) {
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*").matches(name))
            fail(MpsErrorCode.INVALID_MODULE_NAME, "invalid module name: $name")
    }

    private fun validateAlias(alias: String) {
        if (!Regex("[A-Za-z_][A-Za-z0-9_]*").matches(alias))
            fail(MpsErrorCode.INVALID_GENERATOR_ALIAS, "invalid generator alias: $alias")
    }

    private fun validateNameFree(name: String) {
        if (project.repository.modules.any { it.moduleName == name })
            fail(MpsErrorCode.MODULE_NAME_COLLISION, "module name already exists in the MPS repository: $name")
    }

    private fun resolveLanguage(target: String): Language {
        val matches = project.repository.modules.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }
        if (matches.size != 1) fail(if (matches.isEmpty()) MpsErrorCode.TARGET_NOT_FOUND else MpsErrorCode.AMBIGUOUS_TARGET,
            if (matches.isEmpty()) "language not found: $target" else "ambiguous language: $target")
        return matches.single() as? Language ?: fail(MpsErrorCode.INVALID_REQUEST, "source module is not a language: $target")
    }

    private fun nextGeneratorName(languageName: String): String = generateSequence(0) { it + 1 }.map {
        languageName + ".generator" + if (it == 0) "" else it
    }.first { candidate -> project.repository.modules.none { it.moduleName == candidate } }

    private fun firstEmbeddedGeneratorDirectory(languageDirectory: Path): Path = generateSequence(0) { it + 1 }.map {
        languageDirectory.resolve("generator" + if (it == 0) "" else it)
    }.first { !it.exists() || (it.isDirectory() && Files.list(it).use { files -> files.findAny().isEmpty }) }

    private fun planEntry(name: String, kind: ModuleKind, descriptor: Path, source: String? = null, alias: String? = null,
                          persistence: GeneratorPersistence? = null, generatorDirectory: String? = null,
                          facets: List<FacetMementoJson> = emptyList()) =
        ModuleCreationPlanEntry(name, kind, descriptor.toString(), source, alias, persistence, generatorDirectory, facets)

    private fun plannedFacets(preset: SolutionUsagePreset): List<FacetMementoJson> = when (preset) {
        SolutionUsagePreset.NOT_GENERATED -> emptyList()
        SolutionUsagePreset.TEXT -> listOf(FacetMementoJson("plaintext", mapOf("folders" to "true", "root" to "\${module}/source_gen")))
        SolutionUsagePreset.JAVA -> listOf(FacetMementoJson("java", mapOf("compile" to "mps", "classes" to "mps", "ext" to "no")))
        SolutionUsagePreset.JAVA_TESTS -> listOf(
            FacetMementoJson("java", mapOf("compile" to "mps", "classes" to "mps", "ext" to "no")),
            FacetMementoJson("tests"),
        )
        SolutionUsagePreset.JAVA_MPS_PLUGIN -> listOf(FacetMementoJson("java", mapOf("compile" to "mps", "classes" to "mps", "ext" to "yes")))
    }

    fun persist() { project.repository.saveAll(); mpsProject.save() }
    private fun prepareDescriptor(descriptor: Path) {
        descriptor.parent.createDirectories()
        Files.createFile(descriptor)
    }

    private fun file(path: Path) = mpsProject.fileSystem.getFile(path.toString())
    private fun fail(code: MpsErrorCode, message: String): Nothing = throw MpsRequestException(code, message)
}
