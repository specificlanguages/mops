package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ConceptTargetJson
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NamePatternTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind

// Empty `find` responses for tests of request dispatch, which pass responses through without inspecting them.

private val testScope = SearchScopeJson(SearchScopeKind.EDITABLE_PROJECT_SOURCES, models = 1)

internal fun emptyInstancesResponse(limit: Int, truncated: Boolean = false) = FindInstancesResponse(
    target = ConceptTargetJson("example.structure.Target"),
    scope = testScope,
    limit = limit,
    truncated = truncated,
    nodes = emptyList(),
)

internal fun emptyUsagesResponse(limit: Int) = FindUsagesResponse(
    target = MpsNodeSummaryJson(
        type = "root",
        name = "Target",
        concept = "example.structure.Target",
        reference = "r:00000000-0000-0000-0000-000000000001(example.model)/1",
    ),
    scope = testScope,
    limit = limit,
    truncated = false,
    usages = emptyList(),
)

internal fun emptyByNameResponse(limit: Int) = FindByNameResponse(
    target = NamePatternTargetJson("Target*"),
    scope = testScope,
    limit = limit,
    truncated = false,
    nodes = emptyList(),
)
