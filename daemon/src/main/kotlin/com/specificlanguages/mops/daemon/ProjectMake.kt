package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MakeMessageJson
import com.specificlanguages.mops.protocol.MakeMessageKind
import com.specificlanguages.mops.protocol.MakeOutcome
import com.specificlanguages.mops.protocol.MakeResponse
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.dependencies.MakeSequence
import jetbrains.mps.make.resources.IResource
import jetbrains.mps.make.script.IScriptController
import jetbrains.mps.make.service.CoreMakeTask
import jetbrains.mps.messages.IMessage
import jetbrains.mps.messages.IMessageHandler
import jetbrains.mps.messages.MessageKind
import jetbrains.mps.progress.EmptyProgressMonitor
import jetbrains.mps.project.Project
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.resources.ModelsToResources
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Runs the MPS make (generation and compilation) on a set of modules.
 *
 * The make framework builds exactly the modules whose models it is handed — it never pulls in a dependency that is not
 * in the set, it only orders what it is given. So to make un-made dependencies too, [makeModules] first expands the
 * requested modules to their transitive compile-dependency closure ([GlobalModuleDependenciesManager]); already-built
 * library dependencies drop out because [ModelsToResources] keeps only generatable models. The make itself is
 * incremental (clean = false), so up-to-date models in the closure are cheap.
 *
 * Model collection runs inside short read actions; the make call runs outside any model action, on the calling thread,
 * because the make framework acquires its own model locks.
 *
 * See `docs/mps/make-generation.md` for the verified API contract behind this.
 */
class ProjectMake(private val project: Project) {
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance()

    fun makeModules(moduleNames: List<String>): MakeResponse {
        val models = project.modelAccess.computeReadAction<List<SModel>> {
            collectMakeSet(moduleNames.map(::resolveProjectModule)).flatMap { it.models }
        }
        return runMake(models)
    }

    fun makeProject(): MakeResponse {
        val models = project.modelAccess.computeReadAction<List<SModel>> {
            project.projectModulesWithGenerators.flatMap { it.models }
        }
        return runMake(models)
    }

    private fun runMake(models: List<SModel>): MakeResponse {
        // resources() reads the model graph and keeps only generatable models, grouped one MResource per module.
        val resources: List<IResource> = project.modelAccess.computeReadAction<List<IResource>> {
            ModelsToResources(models).resources().toList()
        }

        if (resources.isEmpty()) {
            return MakeResponse(MakeOutcome.NOTHING_TO_GENERATE, moduleCount = 0, messages = emptyList())
        }

        val handler = CollectingMessageHandler()
        val session = MakeSession(project, handler, /* cleanMake = */ false)
        // Reimplements BuildMakeService.doMake using classes from mps-core: BuildMakeService itself ships only in
        // lib/mpsant/mps-tool.jar, which is not on the daemon classpath. A null script lets MPS build the correct
        // per-cluster script; a null-controller default is IScriptController.Stub2. See docs/mps/make-generation.md.
        val controller: IScriptController = IScriptController.Stub2(session)
        val task = CoreMakeTask(MakeSequence(resources, null, session), controller, handler)
        task.run(EmptyProgressMonitor())
        val result = task.getResult()

        // MPS's make result is not reliable on its own — it can report success while errors were reported — so a run
        // counts as successful only when the result is successful AND no error message was seen.
        val succeeded = result != null && result.isSucessful && !handler.errorOccurred
        return MakeResponse(
            outcome = if (succeeded) MakeOutcome.SUCCESS else MakeOutcome.FAILED,
            moduleCount = resources.size,
            messages = handler.messages,
        )
    }

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
     * Collects the error and warning messages MPS reports during the make and records whether any error was seen. A
     * message with no kind is treated as an error, matching how MPS's own headless tooling handles it.
     */
    private class CollectingMessageHandler : IMessageHandler {
        val messages = mutableListOf<MakeMessageJson>()
        var errorOccurred = false
            private set

        override fun handle(msg: IMessage) {
            when (msg.kind) {
                MessageKind.INFORMATION -> Unit
                MessageKind.WARNING -> messages.add(MakeMessageJson(MakeMessageKind.WARNING, msg.text.orEmpty()))
                MessageKind.ERROR -> record(msg)
                else -> record(msg) // null kind: treat as error
            }
        }

        private fun record(msg: IMessage) {
            errorOccurred = true
            messages.add(MakeMessageJson(MakeMessageKind.ERROR, msg.text.orEmpty()))
        }
    }
}
