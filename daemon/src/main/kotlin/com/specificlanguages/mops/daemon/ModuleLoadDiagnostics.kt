package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleDiagnosticResponse
import com.specificlanguages.mops.protocol.ModuleLoadDiagnosticJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadSummary
import com.specificlanguages.mops.protocol.ModulesDiagnosticsResponse
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.DevKit
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.generator.ModelGenerationStatusManager
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.language.LanguageRegistry
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import java.util.stream.Stream

/**
 * Explains why the project's languages and Java-bearing modules did or did not load, by reading MPS's live module
 * runtimes and module repository. See `docs/mps/language-runtime-loading.md` for the API contracts this relies on.
 *
 * A module is "loaded" when it has a registered runtime ([LanguageRegistry.withModuleRuntime]); only loaded languages
 * contribute concepts to name-based lookup, which is why an unloaded language is what makes `find instances` report a
 * concept as not found. When a module is not loaded, its cause is classified from stable openapi (module presence, a
 * Java facet and its settings, whether classes are built, and dependency resolution) rather than MPS's internal
 * classloading graph, which is version-volatile. Dependency analysis recurses, so a module blocked by a dependency
 * that is present-but-not-loaded is explained down to the root modules to fix.
 *
 * All methods must run inside an MPS read action.
 */
/**
 * Flattens a module load [problem] tree to the root modules that must be fixed — its leaves — one per line,
 * de-duplicated: `  - <module>: <reason> — <detail>`. Shared by every caller that turns a diagnosis into a message.
 */
internal fun moduleLoadRootCauseLines(problem: ModuleLoadProblemJson): String =
    moduleLoadRootCauses(problem).distinctBy { it.module to it.reason }.joinToString("\n") { leaf ->
        "  - ${leaf.module}: ${leaf.reason}${leaf.detail?.let { " — $it" } ?: ""}"
    }

private fun moduleLoadRootCauses(problem: ModuleLoadProblemJson): List<ModuleLoadProblemJson> =
    if (problem.causes.isEmpty()) listOf(problem) else problem.causes.flatMap(::moduleLoadRootCauses)

/** Why a project language's compiled runtime cannot be trusted for name-based resolution. */
enum class LanguageUnusableReason { UNBUILT, STALE }

/**
 * A project language whose compiled runtime must not be used for name-based concept resolution: it is either not built
 * ([LanguageUnusableReason.UNBUILT]) or built from older sources than the files on disk
 * ([LanguageUnusableReason.STALE]). Both fail the same way — a name may resolve to a concept whose identity contradicts
 * the sources — so callers treat them alike and only distinguish [reason] in the message they show.
 */
data class UnusableLanguage(val name: String, val reason: LanguageUnusableReason) {
    val explanation: String
        get() = when (reason) {
            LanguageUnusableReason.UNBUILT -> "not built"
            LanguageUnusableReason.STALE -> "built from older sources than the files on disk"
        }
}

/**
 * Refusal message for an operation blocked because [subject] (a model, a concept name, …) would resolve through
 * [languages] whose runtimes are unbuilt or stale. Lists each with its reason and the make command that fixes it.
 */
fun unusableLanguagesMessage(subject: String, languages: List<UnusableLanguage>): String =
    "$subject cannot be used while these project languages are not up to date, since name-based resolution through " +
        "them may return a concept whose identity contradicts the sources:\n" +
        languages.joinToString("\n") { "  - ${it.name}: ${it.explanation}" } +
        "\nrebuild them, for example 'mops make modules ${languages.first().name}'."

class ModuleLoadDiagnostics(private val project: Project) {

    private val repository = project.repository
    private val languageRegistry: LanguageRegistry = project.getComponent(LanguageRegistry::class.java)
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance()
    // Absent only if the generator platform component were unregistered (it is not, headless — the command-line make
    // worker obtains it the same way); when absent, staleness cannot be judged and only the unbuilt case is reported.
    private val generationStatus: ModelGenerationStatusManager? =
        project.getComponent(ModelGenerationStatusManager::class.java)

    fun diagnoseModules(): ModulesDiagnosticsResponse {
        val entries = mutableListOf<ModuleLoadDiagnosticJson>()
        val covered = hashSetOf<SModuleReference>()

        // All languages the project uses or defines.
        for (language in projectLanguages()) {
            val module = language.sourceModule
            if (module == null) {
                entries.add(absentDiagnostic(language.qualifiedName, "language"))
            } else if (covered.add(module.moduleReference)) {
                entries.add(describe(module))
            }
        }
        // Every other project module that carries Java classes; modules without a Java facet are left for `diagnose
        // module <ref>` so this list stays about things expected to load.
        for (module in project.projectModulesWithGenerators) {
            if (module is Language || module !is AbstractModule) continue
            if (module.getFacet(JavaModuleFacet::class.java) == null) continue
            if (covered.add(module.moduleReference)) {
                entries.add(describe(module))
            }
        }

        val ordered = entries.sortedBy { it.module }
        val loaded = ordered.count { it.loaded }
        return ModulesDiagnosticsResponse(
            summary = ModuleLoadSummary(total = ordered.size, loaded = loaded, failed = ordered.size - loaded),
            modules = ordered,
        )
    }

    /**
     * The project's languages, across the whole project, whose runtime is unbuilt or stale (built from older sources
     * than the files on disk). A caller resolving a bare short concept name uses this to refuse guessing uniqueness:
     * counting matches is trustworthy only when every project language is up to date, since an unbuilt language could
     * carry the name unseen and a stale one could carry it under the wrong identity. Sorted by name, one per language.
     * Must run inside a read action.
     */
    fun unusableProjectLanguages(): List<UnusableLanguage> =
        projectLanguages()
            .mapNotNull { it.sourceModule }
            .distinctBy { it.moduleReference }
            .mapNotNull(::classifyUsability)
            .distinctBy { it.name }
            .sortedBy { it.name }

    /**
     * The project languages reachable from [usedLanguages] through the used-language graph whose runtime is unbuilt or
     * stale. Walks used languages to a fixpoint over project language modules — the same closure `make` builds (see
     * [ProjectMake]) — because MPS treats a used language as an already-built dependency and never surfaces its source
     * module through a plain dependency query, so a language used only transitively (a language a used language itself
     * uses) would otherwise be missed. Non-project (library) languages cannot be rebuilt here and are skipped. Sorted
     * by name, one per language. Must run inside a read action.
     */
    fun unusableUsedLanguages(usedLanguages: Collection<SLanguage>): List<UnusableLanguage> {
        val projectLanguageModules = projectLanguageModulesByLanguage()
        val found = linkedMapOf<String, UnusableLanguage>()
        val visited = hashSetOf<SModule>()
        val queued = HashSet(usedLanguages)
        val queue = ArrayDeque(usedLanguages)
        while (queue.isNotEmpty()) {
            val module = projectLanguageModules[queue.removeFirst()] ?: continue
            if (!visited.add(module)) continue
            classifyUsability(module)?.let { found.putIfAbsent(it.name, it) }
            for (used in module.usedLanguages) {
                if (queued.add(used)) queue.addLast(used)
            }
        }
        return found.values.sortedBy { it.name }
    }

    /**
     * Classifies a module as unbuilt, stale, or usable (null). Unbuilt takes precedence — a stale check needs a
     * runtime. A read-only (packaged, jar-shipped) module is never stale in our sense: its classes come pre-built from
     * the classpath and cannot be regenerated here, yet its bundled source models report `generationRequired` (they
     * carry no reachable generation cache), so it must be excluded or every project would look stale through its
     * library languages.
     */
    private fun classifyUsability(module: SModule): UnusableLanguage? = when {
        !runtimeLoaded(module) -> UnusableLanguage(nameOf(module), LanguageUnusableReason.UNBUILT)
        (module as? AbstractModule)?.isReadOnly == true -> null
        isStale(module) -> UnusableLanguage(nameOf(module), LanguageUnusableReason.STALE)
        else -> null
    }

    /**
     * Whether any of [module]'s models needs regeneration — its current content differs from what was recorded when it
     * was last generated — which means the module's compiled runtime is out of date with its sources. See
     * `docs/mps/model-generation-status.md`.
     */
    private fun isStale(module: SModule): Boolean {
        val generationStatus = generationStatus ?: return false
        return module.models.any { generationStatus.generationRequired(it) }
    }

    private fun projectLanguageModulesByLanguage(): Map<SLanguage, Language> =
        project.getProjectModules(Language::class.java)
            .associateBy { MetaAdapterFactory.getLanguage(it.moduleReference) }

    fun diagnoseModule(reference: String): ModuleDiagnosticResponse {
        val module = resolveModule(reference)
            ?: return ModuleDiagnosticResponse(absentDiagnostic(reference, "unknown"))
        return ModuleDiagnosticResponse(describe(module))
    }

    // Each module is analysed with its own memo: loaded dependencies short-circuit before recursion, so sharing a memo
    // across the scan buys little, and a memo left in a tentative state by a dependency cycle would otherwise leak an
    // inconsistent result into later modules.
    private fun describe(module: SModule): ModuleLoadDiagnosticJson {
        val problem = analyze(module, hashMapOf(), hashSetOf())
        return ModuleLoadDiagnosticJson(
            module = nameOf(module),
            kind = kindOf(module),
            present = true,
            loaded = problem == null,
            problem = problem?.toJson(),
        )
    }

    /** Returns the module's load problem, or null when the module is loaded. */
    private fun analyze(
        module: SModule,
        memo: MutableMap<SModuleReference, Problem?>,
        inProgress: MutableSet<SModuleReference>,
    ): Problem? {
        val ref = module.moduleReference
        if (memo.containsKey(ref)) return memo[ref]
        if (runtimeLoaded(module)) return null.also { memo[ref] = null }

        if (module !is AbstractModule) {
            return Problem(nameOf(module), NOT_A_MODULE, "not a class-loadable module", blocking = true)
                .also { memo[ref] = it }
        }

        ownProblem(module)?.let { return it.also { p -> memo[ref] = p } }

        // The module itself is fine but is not loaded: attribute it to its dependencies. A dependency edge back to a
        // module already on the current path is a legal cycle (e.g. a language and its runtime solution depend on each
        // other) and is not itself a failure, so it contributes no cause.
        if (!inProgress.add(ref)) return null
        val causes = mutableListOf<Problem>()
        var sawCycle = false
        val seenDeps = hashSetOf<SModuleReference>()
        for (dep in dependencies(module)) {
            if (!seenDeps.add(dep.reference)) continue
            val target = dep.target
            when {
                target == null -> causes.add(Problem(dep.name, ABSENT, "not present in the repository", blocking = true))
                inProgress.contains(target.moduleReference) -> sawCycle = true
                else -> analyze(target, memo, inProgress)?.let { if (it.blocking) causes.add(it) }
            }
        }
        inProgress.remove(ref)

        if (causes.isNotEmpty()) {
            return Problem(nameOf(module), BROKEN_DEPENDENCIES, "modules it depends on are not loaded", blocking = true, causes = causes)
                .also { memo[ref] = it }
        }
        // No blocking cause. When the only thing left unresolved was a dependency cycle, defer: this module is not
        // independently broken, and diagnosing a cycle member as a top-level module (with an empty path) surfaces the
        // shared root cause. A cycle-derived result depends on the current path, so it is not memoized.
        if (sawCycle) return null
        return Problem(
            nameOf(module), RUNTIME_LOAD_FAILED,
            "classes and dependencies are present, but the runtime did not register; check the daemon log for " +
                "\"Missing language runtime class\" or a LinkageError",
            blocking = true,
        ).also { memo[ref] = it }
    }

    /** A module's own (non-dependency) load problem, or null when the module itself is fit to load. */
    private fun ownProblem(module: AbstractModule): Problem? {
        val name = nameOf(module)
        val facet = module.getFacet(JavaModuleFacet::class.java)
        if (facet == null) {
            // A language must ship classes; any other module may legitimately have no Java code, so its missing facet
            // is reported but does not block modules that depend on it.
            return if (module is Language) {
                Problem(name, NO_JAVA_FACET, "a language must ship compiled classes but has no Java module facet", blocking = true)
            } else {
                Problem(name, NO_JAVA_FACET, "the module has no Java module facet, so it loads no Java classes", blocking = false)
            }
        }
        if (!facet.loadClasses.classesAvailable()) {
            return Problem(name, CLASSES_DISABLED, "the Java module facet is configured not to load classes into MPS", blocking = true)
        }
        if (!isBuilt(facet)) {
            return Problem(name, NOT_BUILT, "the module should have classes but they have not been built yet", blocking = true)
        }
        return null
    }

    private fun isBuilt(facet: JavaModuleFacet): Boolean {
        // A deployed/packaged module has no design-time classes output; its classes come from the classpath, so it is
        // built by construction. A source module's classes live under classesGen, absent or empty when never built.
        val classesGen = facet.classesGen ?: return true
        return classesGen.exists() && !classesGen.children.isNullOrEmpty()
    }

    private fun runtimeLoaded(module: SModule): Boolean {
        var loaded = false
        languageRegistry.withModuleRuntime(Stream.of(module.moduleReference)) { loaded = true }
        return loaded
    }

    private fun dependencies(module: SModule): List<Dep> {
        val deps = mutableListOf<Dep>()
        for (dependency in module.declaredDependencies) {
            deps.add(Dep(dependency.target, dependency.targetModule, nameOf(dependency.targetModule)))
        }
        for (used in module.usedLanguages) {
            deps.add(Dep(used.sourceModule, used.sourceModuleReference, used.qualifiedName))
        }
        if (module is Language) {
            for (runtimeRef in module.runtimeModulesReferences) {
                deps.add(Dep(runtimeRef.resolve(repository), runtimeRef, nameOf(runtimeRef)))
            }
        }
        return deps
    }

    /** Languages used by any project module, plus those the project defines (which may have no model that uses them). */
    private fun projectLanguages(): Set<SLanguage> {
        val languages = project.projectModulesWithGenerators.flatMapTo(sortedSetLanguages()) { it.usedLanguages }
        project.getProjectModules(Language::class.java).forEach {
            languages.add(MetaAdapterFactory.getLanguage(it.moduleReference))
        }
        return languages
    }

    private fun sortedSetLanguages(): MutableSet<SLanguage> = sortedSetOf(compareBy { it.qualifiedName })

    private fun resolveModule(reference: String): SModule? {
        runCatching { persistence.createModuleReference(reference).resolve(repository) }.getOrNull()?.let { return it }
        return repository.modules.firstOrNull { it.moduleName == reference }
    }

    private fun kindOf(module: SModule): String = when (module) {
        is Language -> "language"
        is Generator -> "generator"
        is DevKit -> "devkit"
        is Solution -> "solution"
        else -> "other"
    }

    private fun absentDiagnostic(name: String, kind: String): ModuleLoadDiagnosticJson =
        ModuleLoadDiagnosticJson(
            module = name,
            kind = kind,
            present = false,
            loaded = false,
            problem = ModuleLoadProblemJson(name, ABSENT, "not present in the repository"),
        )

    private fun nameOf(module: SModule): String = module.moduleName ?: module.moduleReference.toString()

    private fun nameOf(reference: SModuleReference): String = reference.moduleName ?: reference.toString()

    private class Dep(val target: SModule?, val reference: SModuleReference, val name: String)

    private class Problem(
        val module: String,
        val reason: String,
        val detail: String?,
        val blocking: Boolean,
        val causes: List<Problem> = emptyList(),
    ) {
        fun toJson(): ModuleLoadProblemJson =
            ModuleLoadProblemJson(module = module, reason = reason, detail = detail, causes = causes.map { it.toJson() })
    }

    private companion object {
        const val ABSENT = "ABSENT"
        const val NOT_A_MODULE = "NOT_A_MODULE"
        const val NO_JAVA_FACET = "NO_JAVA_FACET"
        const val CLASSES_DISABLED = "CLASSES_DISABLED"
        const val NOT_BUILT = "NOT_BUILT"
        const val BROKEN_DEPENDENCIES = "BROKEN_DEPENDENCIES"
        const val RUNTIME_LOAD_FAILED = "RUNTIME_LOAD_FAILED"
    }
}
