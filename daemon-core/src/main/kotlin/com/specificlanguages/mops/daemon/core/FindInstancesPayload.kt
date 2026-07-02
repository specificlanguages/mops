package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.MpsNodeSummaryJson

data class FindInstancesPayload(
    val limit: Int,
    val truncated: Boolean,
    val nodes: List<MpsNodeSummaryJson>,
)
