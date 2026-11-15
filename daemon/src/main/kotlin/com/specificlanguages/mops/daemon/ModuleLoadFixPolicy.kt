package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson
import com.specificlanguages.mops.protocol.ModuleLoadProblemReason
import com.specificlanguages.mops.protocol.ModuleLoadProblemReason.*

/**
 * Logic. Decides the fix for a module load problem and for a language whose generated output may not match its
 * sources.
 *
 * Hides: which problems mops knows a fix for, and what that fix is.
 */
object ModuleLoadFixPolicy {

    /**
     * The fix for a problem with [reason] on [module]. [inProject] is whether the module is a project module, since
     * `mops make module` takes project modules only. [dependencyCauses] are the blocking problems of the modules that
     * a [NOT_BUILT] module depends on: making the module also makes its dependencies, so the make is its fix only when
     * every root cause among them has a fix too.
     */
    fun forProblem(
        reason: ModuleLoadProblemReason,
        module: String,
        inProject: Boolean,
        dependencyCauses: List<ModuleLoadProblemJson> = emptyList(),
    ): ModuleLoadFix? = when (reason) {
        NOT_BUILT -> make(module, inProject).takeIf { dependencyCauses.flatMap { it.rootCauses }.all { it.fix != null } }
        ABSENT, NOT_A_MODULE, NO_JAVA_FACET, CLASSES_DISABLED, BROKEN_DEPENDENCIES, RUNTIME_LOAD_FAILED -> null
    }

    /**
     * The fix for [language] whose models have the generation [evidence]. A make regenerates the models and rewrites
     * their generation records, which clears the evidence only when it compared a computed source hash against a known
     * record location. Unsaved changes, an unavailable source hash or an unknown record location stay after a make.
     */
    fun forGeneration(language: String, inProject: Boolean, evidence: List<ModelGenerationEvidence>): ModuleLoadFix? =
        make(language, inProject).takeIf { evidence.all { it.currentHash != null && it.generationRecord != null } }

    private fun make(module: String, inProject: Boolean): ModuleLoadFix? =
        if (inProject) ModuleLoadFix(listOf(module)) else null
}
