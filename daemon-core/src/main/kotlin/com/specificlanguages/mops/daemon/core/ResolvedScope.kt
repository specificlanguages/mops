package com.specificlanguages.mops.daemon.core

/**
 * A validated **Search Scope**, produced by [MpsRead.resolveScope] from an optional `in`-clause segment list and
 * consumed by [MpsRead.findInstances] and [MpsRead.findUsages].
 *
 * Resolution — counting matches, reporting ambiguity, failing on a missing segment — happens once, in `resolveScope`.
 * The result names its target by serialized reference so a search re-resolves it deterministically; this keeps scope
 * resolution testable on its own, separate from the searches that run over it.
 */
sealed interface ResolvedScope {
    /** The default scope: only editable project sources. */
    data object EditableProjectSources : ResolvedScope

    /** The whole MPS repository, including read-only libraries and stubs (`in /`). */
    data object Repository : ResolvedScope

    /** One MPS module, by serialized module reference. */
    data class Module(val moduleReference: String) : ResolvedScope

    /** One MPS model, by serialized model reference. */
    data class Model(val modelReference: String) : ResolvedScope

    /** One node's subtree — a root or nested node and its descendants — by serialized node reference. */
    data class Subtree(val nodeReference: String) : ResolvedScope
}
