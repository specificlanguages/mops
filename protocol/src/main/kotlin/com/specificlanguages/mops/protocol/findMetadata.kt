package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The kind of portion of the MPS Repository a `find` searched. */
@Serializable
enum class SearchScopeKind {
    /** The default Search Scope: editable project sources. */
    @SerialName("editable-project-sources")
    EDITABLE_PROJECT_SOURCES,

    /** The whole MPS Repository (`in /`). */
    @SerialName("repository")
    REPOSITORY,

    @SerialName("module")
    MODULE,

    @SerialName("model")
    MODEL,

    /** One node and its descendants. */
    @SerialName("subtree")
    SUBTREE,

    /** The concept hierarchies of all loaded language runtimes, which `find subconcepts` searches. */
    @SerialName("loaded-languages")
    LOADED_LANGUAGES,
}

/**
 * The Search Scope a `find` searched, as the daemon resolved it. [reference] and [name] identify the module, model, or
 * subtree node of an explicit scope and are absent for the other kinds. [models] is the number of models searched; it
 * is absent for a subtree scope and for loaded languages, which are not searched model by model.
 */
@Serializable
data class SearchScopeJson(
    val kind: SearchScopeKind,
    val reference: String? = null,
    val name: String? = null,
    val models: Int? = null,
)

/** The concept a `find instances` or `find subconcepts` searched for, as its resolved qualified name. */
@Serializable
data class ConceptTargetJson(val concept: String)

/** The name pattern a `find root-by-name` matched root names against. */
@Serializable
data class NamePatternTargetJson(val pattern: String)

/** The Node ID a `find node-by-id` looked for, in the decimal spelling mops prints. */
@Serializable
data class NodeIdTargetJson(val nodeId: String)
