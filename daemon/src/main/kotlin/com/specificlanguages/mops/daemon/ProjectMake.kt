package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import jetbrains.mps.generator.GenerationFacade
import jetbrains.mps.generator.GenerationSettingsProvider
import jetbrains.mps.generator.IModifiableGenerationSettings
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.dependencies.MakeSequence
import jetbrains.mps.make.resources.IResource
import jetbrains.mps.make.script.IResult
import jetbrains.mps.make.script.IScriptController
import jetbrains.mps.make.service.CoreMakeTask
import jetbrains.mps.messages.IMessage
import jetbrains.mps.messages.IMessageHandler
import jetbrains.mps.messages.MessageKind
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.project.facets.GenerationTargetFacet
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.resources.ModelsToResources
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.util.ProgressMonitor

/**
 * Runs the MPS make (generation and compilation) on a set of modules.
 *
 * The make framework builds exactly the modules whose models it is handed — it never pulls in a dependency that is not
 * in the set, it only orders what it is given. So to make un-made dependencies too, [makeModules] first expands the
 * requested modules to their transitive compile-dependency closure ([GlobalModuleDependenciesManager]); already-built
 * library dependencies drop out because [ModelsToResources] keeps only generatable models. Models of modules without
 * generation-target facets are left out too ([makeSet]). Every model handed to the make is generated; nothing skips
 * unmodified models.
 *
 * Model collection runs inside short read actions; the make call runs outside any model action, on the calling thread,
 * because the make framework acquires its own model locks.
 *
 * The generation options of a make request reach the Generate facet as generation options of that make session only
 * ([makeGenerationOptions]), except saving transient models and performance tracing, which the Generate facet reads
 * from the process-wide generation settings; those two are set for the duration of the make
 * ([withMakeGenerationSettings]). See
 * https://github.com/specificlanguages/mps-api-research/blob/main/docs/generation-options-per-make.md.
 *
 * See https://github.com/specificlanguages/mps-api-research/blob/main/make-generation.md for the verified API contract.
 */
class ProjectMake(private val project: Project) {
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance()

    /**
     * Makes [moduleNames] and their dependencies with the [generation] options (see [runMake]). The make checks [monitor]
     * for cancellation between its steps.
     */
    fun makeModules(
        moduleNames: List<String>,
        monitor: ProgressMonitor = EmptyProgressMonitor(),
        generation: MakeGenerationOptions = MakeGenerationOptions(),
    ): MakeResponse {
        val models = project.modelAccess.computeReadAction<List<SModel>> {
            collectMakeSet(moduleNames.map(::resolveProjectModule)).flatMap { it.models }
        }
        return runMake(models, generation, monitor)
    }

    /** Makes every project module with the [generation] options (see [runMake]). */
    fun makeProject(generation: MakeGenerationOptions = MakeGenerationOptions()): MakeResponse {
        val models = project.modelAccess.computeReadAction<List<SModel>> {
            project.projectModulesWithGenerators.flatMap { it.models }
        }
        return runMake(models, generation, EmptyProgressMonitor())
    }

    private val generationSettings: IModifiableGenerationSettings
        get() = project.getComponent(GenerationSettingsProvider::class.java).generationSettings

    /**
     * Makes [models]. MPS generates each model it is handed in full, so the make writes every current output file again
     * (or keeps it when its content is unchanged).
     *
     * The make is clean: MPS treats every file under the output roots of the made modules as stale, so files that no
     * model generates any more are deleted, even when no earlier make recorded them. Clean make is a property of each
     * module's make resource (MakeKeys.CLEAN_MAKE), not of the make session.
     *
     * The response carries the messages of every make step that [MakeGenerationOptions.messages] reports.
     */
    private fun runMake(
        models: List<SModel>,
        generation: MakeGenerationOptions,
        monitor: ProgressMonitor,
    ): MakeResponse {
        val options = makeGenerationOptions(generationSettings, generation)
        val makeSet = project.modelAccess.computeReadAction<MakeSet> { makeSet(models) }
        val handler = CollectingMessageHandler()
        makeSet.leftOutModules.mapTo(handler.messages) {
            MakeMessageJson(MakeMessageKind.WARNING, it.notGeneratedMessage())
        }

        // MakeSession's own clean flag only names the IDE's make script; the resources above carry the clean make.
        val session = MakeSession(project, handler, /* cleanMake = */ false)
        // The make sequence leaves out a module whose models use no language and import no language or devkit, so it
        // can have no step even when there are resources.
        val sequence = MakeSequence(makeSet.resources, null, session)
        if (sequence.steps() == 0) {
            return MakeResponse(MakeOutcome.NOTHING_TO_GENERATE, 0, handler.messages.reportedAt(generation.messages))
        }

        // Reimplements BuildMakeService.doMake using classes from mps-core: BuildMakeService itself ships only in
        // lib/mpsant/mps-tool.jar, which is not on the daemon classpath. A null script lets MPS build the correct
        // per-cluster script; a null-controller default is IScriptController.Stub2. See the mps-api-research notes.
        val controller: IScriptController = makeScriptController(session, options)
        val task = CoreMakeTask(sequence, controller, handler)
        val result = withMakeGenerationSettings(generationSettings, generation) {
            task.run(monitor)
            task.getResult()
        }

        return makeResponse(result, handler.messages, makeSet.resources.size, generation.messages)
    }

    /**
     * The make resources for [models], grouped one per module, and the modules whose generatable models are left out
     * because they have nowhere to write generated files. Must run inside a read action.
     *
     * Models of read-only modules are left out: MPS ships its library languages and runtime solutions as *source*
     * models inside `-src.jar` files, and those models report themselves generatable, yet writing their generation
     * output into the jar fails ("Write for jar files is not supported"). Models whose module has no generation-target
     * facets are left out too: text generation has no output location for them, which fails the make on MPS before
     * 2025.3. See https://github.com/specificlanguages/mps-api-research/blob/main/make-generation.md.
     */
    private fun makeSet(models: List<SModel>): MakeSet {
        val writable = models.filter { it.isInWritableModule() }
        val (withTarget, withoutTarget) = writable.partition { GenerationTargetFacet.find(it) != null }
        val leftOutModules = withoutTarget.filter(GenerationFacade::canGenerate).mapNotNull { it.module }.distinct()
        // resources() keeps only generatable models.
        val resources = ModelsToResources(withTarget, /* cleanMake = */ true).resources().toList()
        return MakeSet(resources, leftOutModules)
    }

    private class MakeSet(val resources: List<IResource>, val leftOutModules: List<SModule>)

    private fun SModule.notGeneratedMessage(): String =
        "not generating the models of $moduleName: the module has no generation-target facets"

    /**
     * The modules to make for a request over [requested]: the requested modules, their module-level compile-dependency
     * closure, and — because MPS treats a used language as an already-built dependency and never adds its source module
     * to a plain module's dependency closure — the project source module of every used language (found by walking the
     * set to a fixpoint) together with its generators. That last part is what makes an un-made language a module depends
     * on get made too. Must run inside a read action. Non-generatable modules in the set are dropped later by
     * [ModelsToResources].
     */
    private fun collectMakeSet(requested: List<SModule>): Set<SModule> {
        val result = LinkedHashSet<SModule>()
        result.addAll(
            GlobalModuleDependenciesManager(requested)
                .getModules(GlobalModuleDependenciesManager.Deptype.COMPILE),
        )

        val projectLanguageModules: Map<SLanguage, Language> = project.projectModulesWithGenerators
            .filterIsInstance<Language>()
            .associateBy { MetaAdapterFactory.getLanguage(it.moduleReference) }

        val queue = ArrayDeque(result)
        val visited = HashSet<SModule>()
        while (queue.isNotEmpty()) {
            val module = queue.removeFirst()
            if (!visited.add(module)) continue
            for (usedLanguage in module.usedLanguages) {
                val languageModule = projectLanguageModules[usedLanguage] ?: continue
                if (result.add(languageModule)) queue.addLast(languageModule)
                for (generator in languageModule.generators) {
                    if (result.add(generator)) queue.addLast(generator)
                }
            }
        }
        return result
    }

    // A model belongs to a writable module when its owning module is not read-only. Read-only modules are the deployed,
    // jar-packaged libraries MPS ships pre-built; they must never be regenerated.
    private fun SModel.isInWritableModule(): Boolean =
        (module as? AbstractModule)?.isReadOnly != true

    private fun resolveProjectModule(name: String): SModule {
        val matches = project.projectModulesWithGenerators.filter {
            it.moduleName == name || persistence.asString(it.moduleReference) == name
        }
        return when (matches.size) {
            0 -> throw MpsRequestException(MpsErrorCode.TARGET_NOT_FOUND, "module not found: $name")
            1 -> matches.single()
            else -> throw MpsRequestException(
                MpsErrorCode.AMBIGUOUS_TARGET,
                "ambiguous module: $name matches ${matches.size} project modules",
            )
        }
    }

    /**
     * Collects the messages MPS reports during the make. A message with no kind is treated as an error, matching how
     * MPS's own headless tooling handles it.
     */
    private class CollectingMessageHandler : IMessageHandler {
        val messages = mutableListOf<MakeMessageJson>()

        override fun handle(msg: IMessage) {
            val kind = when (msg.kind) {
                MessageKind.INFORMATION -> MakeMessageKind.INFO
                MessageKind.WARNING -> MakeMessageKind.WARNING
                else -> MakeMessageKind.ERROR
            }
            messages.add(MakeMessageJson(kind, msg.text.orEmpty()))
        }
    }
}

/**
 * The response for a make that ran with [result] and reported [messages], keeping the messages that [level] reports.
 *
 * MPS's make result is not reliable on its own — it can report success while errors were reported — so a make
 * succeeds only when [result] is successful and no error message was seen. A failed make always carries an error: when
 * MPS reported none, the response says so with the reason from [result], if any.
 */
internal fun makeResponse(
    result: IResult?,
    messages: List<MakeMessageJson>,
    moduleCount: Int,
    level: MakeMessageLevel?,
): MakeResponse {
    val hasError = messages.any { it.kind == MakeMessageKind.ERROR }
    val succeeded = result != null && result.isSucessful && !hasError
    val reported = messages.reportedAt(level)
    return MakeResponse(
        outcome = if (succeeded) MakeOutcome.SUCCESS else MakeOutcome.FAILED,
        moduleCount = moduleCount,
        messages = if (succeeded || hasError) reported else reported + unexplainedFailure(result),
    )
}

private fun List<MakeMessageJson>.reportedAt(level: MakeMessageLevel?): List<MakeMessageJson> =
    filter { level.reports(it.kind) }

private fun unexplainedFailure(result: IResult?): MakeMessageJson {
    val reason = result?.message().orEmpty().ifBlank { null }
    val text = if (reason == null) {
        "make failed without reporting an error"
    } else {
        "make failed without reporting an error: $reason"
    }
    return MakeMessageJson(MakeMessageKind.ERROR, text)
}
