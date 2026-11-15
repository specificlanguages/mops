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
 * Value. A module target that matched no module in its scope, or more than one. [candidates] lists one row per matching
 * module (`module`, name, and serialized reference, tab-separated), sorted; it is empty for a miss.
 */
class ModuleTargetException private constructor(
    val target: String,
    val candidates: List<String>,
    code: MpsErrorCode,
    message: String,
) : MpsRequestException(code, message) {
    internal companion object {
        fun notFound(target: String, scope: ModuleTargetScope) = ModuleTargetException(
            target, emptyList(), MpsErrorCode.MODULE_NOT_FOUND, "module not found in ${scope.label}: $target",
        )

        fun ambiguous(target: String, candidates: List<String>) = ModuleTargetException(
            target, candidates, MpsErrorCode.AMBIGUOUS_TARGET,
            "ambiguous module target $target:\n" + candidates.joinToString("\n"),
        )
    }
}

/**
 * Logic. Resolves a module target, a module name or serialized module reference, to the one module it names.
 *
 * Hides: what text addresses a module, and what zero or several matches mean. Every command, Code Mode lookup, and
 * Navigation Target that resolves a module target goes through here, so a miss and an ambiguity read the same
 * everywhere. Must be called inside a model read action.
 */
internal class ModuleTargetResolver(
    private val project: Project,
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    /** The module [target] names in [scope], or null when none does. Fails when several do. */
    fun find(target: String, scope: ModuleTargetScope): SModule? {
        val matches = matching(target, scope)
        if (matches.size > 1) throw ModuleTargetException.ambiguous(target, matches.map(::candidateRow).sorted())
        return matches.singleOrNull()
    }

    /** The module [target] names in [scope]. Fails when none or several do. */
    fun require(target: String, scope: ModuleTargetScope): SModule =
        find(target, scope) ?: throw ModuleTargetException.notFound(target, scope)

    /**
     * Every module in [scope] that [target] names, for a caller that counts modules among other kinds of match (a
     * single-segment Navigation Target) and reports ambiguity across all of them itself.
     */
    fun matching(target: String, scope: ModuleTargetScope): List<SModule> =
        candidates(scope).filter { it.moduleName == target || persistence.asString(it.moduleReference) == target }

    /** The row that names [module] in an ambiguity listing. */
    fun candidateRow(module: SModule): String =
        "module\t${module.moduleName}\t${persistence.asString(module.moduleReference)}"

    private fun candidates(scope: ModuleTargetScope): Iterable<SModule> = when (scope) {
        ModuleTargetScope.REPOSITORY -> project.repository.modules
        ModuleTargetScope.PROJECT -> project.projectModulesWithGenerators
    }
}
