package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.MpsNodeUsageJson

data class FindUsagesPayload(
    val limit: Int,
    val truncated: Boolean,
    val usages: List<MpsNodeUsageJson>,
)
