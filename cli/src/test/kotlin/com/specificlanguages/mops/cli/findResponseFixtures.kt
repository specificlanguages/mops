package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.ConceptTargetJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.NamePatternTargetJson
import com.specificlanguages.mops.protocol.NodeIdTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind

// Target and scope metadata for `find` responses in tests that do not inspect it.

internal val testScope = SearchScopeJson(SearchScopeKind.EDITABLE_PROJECT_SOURCES, models = 1)

internal val testConceptTarget = ConceptTargetJson("example.structure.Target")

internal val testPatternTarget = NamePatternTargetJson("Target*")

internal val testNodeIdTarget = NodeIdTargetJson("1")

internal val testUsagesTarget = MpsNodeSummaryJson(
    type = "root",
    name = "Target",
    concept = "example.structure.Target",
    reference = "r:00000000-0000-0000-0000-000000000001(example.model)/1",
)
