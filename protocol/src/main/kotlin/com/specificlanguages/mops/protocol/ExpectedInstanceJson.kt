package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedInstanceJson(
    val reference: String,
    val resolved: Boolean,
    val concept: InstanceConceptJson? = null,
    val inScope: Boolean? = null,
    val semanticMatch: Boolean? = null,
    val inExpandedConcepts: Boolean? = null,
    val inFacade: Boolean? = null,
    val inLookup: Boolean? = null,
    val inNormalSearch: Boolean? = null,
    val rejectedFilters: List<NodeFilter> = emptyList(),
    val returned: Boolean? = null,
    val status: String = "unresolved",
)
