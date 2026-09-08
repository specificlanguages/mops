package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.*
import groovy.lang.Closure
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

object CodeModeExtensions {
    private val persistence get() = PersistenceFacade.getInstance()

    /** Returns a live indexed accessor. Reads require model access; writes require command access. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getProperties(node: SNode): NodeProperties = NodeProperties(node)

    /** Returns indexed access to at most one child per role. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getChild(node: SNode): NodeChild = NodeChild(node)

    /** Returns indexed access to child lists by role. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getChildren(node: SNode): NodeChildren = NodeChildren(node)

    /** Returns indexed access to native references by role. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun getReferences(node: SNode): NodeReferences = NodeReferences(node)

    /** Runs a non-nesting MPS read action and returns the closure value. Access: none. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun <T> read(project: Project, body: Closure<T>): T {
        requireOutside(project, "read")
        return CodeModeProjectProvider.access().read { body.call() }
    }

    /** Runs an MPS command on the EDT, saves successful changes, and returns the closure value. Access: none. */
    @CodeModeExtension(MpsAccessLevel.NONE)
    @JvmStatic
    fun <T> command(project: Project, body: Closure<T>): T {
        requireOutside(project, "command")
        return CodeModeProjectProvider.access().write {
            body.call().also {
                project.repository.saveAll()
                (project as? MPSProject)?.save()
            }
        }
    }

    /** Resolves exactly one repository module by name or serialized module reference. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun module(project: Project, target: String): SModule {
        requireOwner(project); requireRead("Project.module")
        val matches = project.repository.modules.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }
        return unique("module", target, matches) { persistence.asString(it.moduleReference) }
    }

    /** Resolves exactly one model by established model target spelling. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun model(project: Project, target: String): SModel {
        requireOwner(project); requireRead("Project.model")
        return ModelNodeResolver(DaemonLogger()).findModelUnique(project, target)
            ?: throw IllegalArgumentException("model not found: $target")
    }

    /** Resolves exactly one node reference. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun node(project: Project, target: String): SNode {
        requireOwner(project); requireRead("Project.node")
        val reference = runCatching { persistence.createNodeReference(target) }.getOrNull()
            ?: throw IllegalArgumentException("invalid node reference: $target")
        return reference.resolve(project.repository) ?: throw IllegalArgumentException("node not found: $target")
    }

    /** Resolves exactly one MPS concept using the established concept-name grammar. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun concept(project: Project, name: String): SAbstractConcept {
        requireOwner(project); requireRead("Project.concept")
        return ConceptResolver(project).resolve(name)
    }

    /** Makes every generatable project module. Access: extra. */
    @CodeModeExtension(MpsAccessLevel.EXTRA)
    @JvmStatic
    fun make(project: Project): MakeResponse {
        requireOwner(project); requireExtra("Project.make")
        return ProjectMake(project).makeProject()
    }

    /** Renders this node with its native MPS editor. Access: extra. */
    @CodeModeExtension(MpsAccessLevel.EXTRA)
    @JvmStatic
    fun render(node: SNode): String {
        requireExtra("SNode.render")
        val project = CodeModeProjectProvider.project()
        val rendered = CompletableFuture<String>()
        project.modelAccess.runReadInEDT {
            try {
                rendered.complete(EditorNodeRenderer().render(node, project))
            } catch (failure: Throwable) {
                rendered.completeExceptionally(failure)
            }
        }
        return try {
            rendered.get()
        } catch (failure: ExecutionException) {
            throw failure.cause ?: failure
        }
    }

    /** Creates and returns a native MPS language. Access: command. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    @JvmOverloads
    fun createLanguage(project: Project, name: String, options: Map<String, Any?> = emptyMap()): Language {
        requireOwner(project); requireCommand("Project.createLanguage"); options.requireOnly(
            "descriptor", "withGenerator"
        )
        return ModuleCreator(project).createLanguage(
            name, options["descriptor"]?.toString(), options["withGenerator"] as? Boolean ?: false,
        ).primary as Language
    }

    /** Creates and returns a native MPS solution. Access: command. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    @JvmOverloads
    fun createSolution(project: Project, name: String, options: Map<String, Any?> = emptyMap()): Solution {
        requireOwner(project); requireCommand("Project.createSolution"); options.requireOnly(
            "descriptor", "usagePreset"
        )
        val preset =
            options["usagePreset"]?.toString()?.replace('-', '_')?.uppercase()?.let(SolutionUsagePreset::valueOf)
                ?: SolutionUsagePreset.NOT_GENERATED
        return ModuleCreator(project).createSolution(name, options["descriptor"]?.toString(), preset).primary as Solution
    }

    /** Creates and returns a native MPS devkit. Access: command. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    @JvmOverloads
    fun createDevkit(project: Project, name: String, options: Map<String, Any?> = emptyMap()): DevKit {
        requireOwner(project); requireCommand("Project.createDevkit"); options.requireOnly("descriptor")
        return ModuleCreator(project).createDevkit(name, options["descriptor"]?.toString()).primary as DevKit
    }

    /** Creates and returns a generator owned by this language. Access: command. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    @JvmOverloads
    fun createGenerator(language: Language, alias: String, options: Map<String, Any?> = emptyMap()): Generator {
        requireCommand("Language.createGenerator"); options.requireOnly("standalone", "descriptor")
        val project = CodeModeProjectProvider.project()
        check(project.isProjectModule(language)) { "language belongs to a different MPS project" }
        return ModuleCreator(project).createGenerator(
            language.moduleName!!, alias, options["standalone"] as? Boolean ?: false, options["descriptor"]?.toString(),
        ).primary as Generator
    }

    /** Creates and returns a model owned by this module. Access: command. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    @JvmOverloads
    fun createModel(module: SModule, name: String, filePerRoot: Boolean = false): SModel {
        requireCommand("SModule.createModel")
        val project = CodeModeProjectProvider.project()
        check(project.isProjectModule(module)) { "module belongs to a different MPS project" }
        return ModelCreator(project).create(module, name, filePerRoot)
    }

    private fun requireOutside(project: Project, kind: String) {
        requireOwner(project)
        check(!project.modelAccess.canRead()) { "Nested access blocks are not allowed; cannot start project.$kind while model access is already held" }
    }

    private fun requireOwner(project: Project) {
        check(CodeModeProjectProvider.project() === project) { "project belongs to a different Code Mode session" }
    }

    private fun Map<String, Any?>.requireOnly(vararg names: String) {
        val unknown = keys - names.toSet()
        require(unknown.isEmpty()) { "unknown named option(s): ${unknown.sorted().joinToString()}" }
    }

    private fun <T> unique(kind: String, target: String, values: List<T>, reference: (T) -> String): T =
        when (values.size) {
            0 -> throw IllegalArgumentException("$kind not found: $target")
            1 -> values.single()
            else -> throw IllegalArgumentException(
                "ambiguous $kind: $target; candidates: ${
                    values.joinToString(
                        transform = reference
                    )
                }"
            )
        }
}
