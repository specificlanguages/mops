package com.specificlanguages.mops.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One predicate over the nodes a search returns. Filters are AND-composed post-filters over a search's node results:
 * each narrows, none widens. They are search-agnostic — the same filter applies to any search that yields nodes — so a
 * request carries them as a list rather than as a fixed set of named fields.
 *
 * The daemon may apply further predicates of its own from context that never reaches the wire: a subtree Search Scope,
 * for instance, contributes a containment predicate to the same chain. Those are not [NodeFilter]s because they are not
 * requested by the caller; they compose with these by the same AND rule.
 */
@Serializable
sealed interface NodeFilter {
    /** Keeps only nodes whose **Node Name** matches [pattern] under MPS Go-to-Node matching (camel-hump and `*`). */
    @Serializable
    @SerialName("named")
    data class Named(val pattern: String) : NodeFilter

    /** Keeps only nodes filling the containment [role] in their parent. A Root Node has no role and never matches. */
    @Serializable
    @SerialName("role")
    data class Role(val role: String) : NodeFilter
}
