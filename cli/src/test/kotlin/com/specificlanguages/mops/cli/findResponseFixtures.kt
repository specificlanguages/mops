package com.specificlanguages.mops.cli

import com.specificlanguages.mops.protocol.ConceptTargetJson
import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindNodeByIdResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MpsNodeParentJson
import com.specificlanguages.mops.protocol.MpsNodeSummaryJson
import com.specificlanguages.mops.protocol.MpsNodeUsageJson
import com.specificlanguages.mops.protocol.NamePatternTargetJson
import com.specificlanguages.mops.protocol.NodeIdTargetJson
import com.specificlanguages.mops.protocol.SearchScopeJson
import com.specificlanguages.mops.protocol.SearchScopeKind

// `find` responses for tests. Every builder has the same defaults: one match, testNode, with the default limit of 100,
// not truncated, in testScope. A test passes only what it checks.

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

internal const val TEST_MODEL = "r:fd752404-89d3-4ffe-bc3a-7fb7a27c63b6(com.specificlanguages.json.structure)"

/** A root `ConceptDeclaration` named `JsonObject`. */
internal val testNode = MpsNodeSummaryJson(
    type = "root",
    name = "JsonObject",
    concept = "jetbrains.mps.lang.structure.structure.ConceptDeclaration",
    reference = "$TEST_MODEL/2110045694544566905",
)

/** A `LinkDeclaration` whose parent is [testNode]. */
internal val testChildNode = MpsNodeSummaryJson(
    type = "node",
    name = "content",
    concept = "jetbrains.mps.lang.structure.structure.LinkDeclaration",
    reference = "$TEST_MODEL/2110045694544566906",
    parent = MpsNodeParentJson(
        type = testNode.type,
        role = "linkDeclaration",
        name = testNode.name,
        concept = testNode.concept,
        reference = testNode.reference,
    ),
)

internal fun instancesResponse(
    nodes: List<MpsNodeSummaryJson> = listOf(testNode),
    limit: Int = 100,
    truncated: Boolean = false,
    scope: SearchScopeJson = testScope,
    target: ConceptTargetJson = testConceptTarget,
) = FindInstancesResponse(target = target, scope = scope, limit = limit, truncated = truncated, nodes = nodes)

internal fun byNameResponse(
    nodes: List<MpsNodeSummaryJson> = listOf(testNode),
    limit: Int = 100,
    truncated: Boolean = false,
    scope: SearchScopeJson = testScope,
    target: NamePatternTargetJson = testPatternTarget,
) = FindByNameResponse(target = target, scope = scope, limit = limit, truncated = truncated, nodes = nodes)

internal fun nodeIdResponse(
    nodes: List<MpsNodeSummaryJson> = listOf(testNode),
    limit: Int = 100,
    truncated: Boolean = false,
    scope: SearchScopeJson = testScope,
    target: NodeIdTargetJson = testNodeIdTarget,
) = FindNodeByIdResponse(target = target, scope = scope, limit = limit, truncated = truncated, nodes = nodes)

/** A `find usages` response with one usage in the `concept` role per node in [owners]. */
internal fun usagesResponse(
    owners: List<MpsNodeSummaryJson> = listOf(testNode),
    limit: Int = 100,
    truncated: Boolean = false,
    scope: SearchScopeJson = testScope,
    target: MpsNodeSummaryJson = testUsagesTarget,
) = FindUsagesResponse(
    target = target,
    scope = scope,
    limit = limit,
    truncated = truncated,
    usages = owners.map { MpsNodeUsageJson(role = "concept", owner = it) },
)
