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
class ModuleLoadDiagnostics(private val project: Project) {

    private val repository = project.repository
    private val languageRegistry: LanguageRegistry = project.getComponent(LanguageRegistry::class.java)
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance()

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
