package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Load diagnosis for one module. [kind] is `language`, `solution`, `generator`, `devkit`, `other`, or `unknown` (when
 * the module could not be resolved). [present] is whether the module resolved in the repository, [loaded] whether its
 * runtime is registered. [problem] is null exactly when [loaded]; otherwise it is the root of the module's load-problem
 * tree (see [ModuleLoadProblemJson]).
 */
@Serializable
data class ModuleLoadDiagnosticJson(
    val module: String,
    val kind: String,
    val present: Boolean,
    val loaded: Boolean,
    val problem: ModuleLoadProblemJson? = null,
)
