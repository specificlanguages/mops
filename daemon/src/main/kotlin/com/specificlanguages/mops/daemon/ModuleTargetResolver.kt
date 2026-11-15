package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/** The modules a module target is looked up among. */
internal enum class ModuleTargetScope(val label: String) {
    /** Every module in the project's repository, libraries and MPS's own modules included. */
    REPOSITORY("repository"),

    /** The project's own modules and their generators. */
    PROJECT("project"),
}

/**
 * Logic. Resolves a module target to the one module it names. A module target is a module name or a serialized module
 * reference; a reference matches the module with its id, whatever name the reference spells.
 *
 * Hides: what text addresses a module, and what zero or several matches mean. Every command, Code Mode lookup, and
 * Navigation Target that resolves a module target goes through here, so a miss and an ambiguity read the same
 * everywhere: [MpsErrorCode.MODULE_NOT_FOUND] naming the scope, or [MpsErrorCode.AMBIGUOUS_TARGET] listing one row
 * per candidate. Must be called inside a model read action.
 */
internal class ModuleTargetResolver(
    private val project: Project,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    /** The module [target] names in [scope], or null when none does. Fails when several do. */
    fun find(target: String, scope: ModuleTargetScope): SModule? {
        val matches = matching(target, scope)
        if (matches.size > 1) throw MpsRequestException(MpsErrorCode.AMBIGUOUS_TARGET, ambiguousMessage(target, matches))
        return matches.singleOrNull()
    }

    /** The module [target] names in [scope]. Fails when none or several do. */
    fun require(target: String, scope: ModuleTargetScope): SModule =
        find(target, scope)
            ?: throw MpsRequestException(MpsErrorCode.MODULE_NOT_FOUND, "module not found in ${scope.label}: $target")

    /**
     * Every module in [scope] that [target] names, for a caller that reports a miss or an ambiguity in its own result
     * type (a Navigation Target), or counts modules among other kinds of match.
     */
    fun matching(target: String, scope: ModuleTargetScope): List<SModule> {
        val id = runCatching { persistence.createModuleReference(target).moduleId }.getOrNull()
        return candidates(scope).filter { it.moduleName == target || (id != null && it.moduleId == id) }
    }

    /** The message that reports [target] matching each of [matches]. */
    fun ambiguousMessage(target: String, matches: List<SModule>): String =
        ambiguousTargetMessage("module", target, matches.map(::candidateRow).sorted())

    /** The row that names [module] in an ambiguity listing. */
    fun candidateRow(module: SModule): String =
        "module\t${module.moduleName}\t${persistence.asString(module.moduleReference)}"

    private fun candidates(scope: ModuleTargetScope): Iterable<SModule> = when (scope) {
        ModuleTargetScope.REPOSITORY -> project.repository.modules
        ModuleTargetScope.PROJECT -> project.projectModulesWithGenerators
    }
}
