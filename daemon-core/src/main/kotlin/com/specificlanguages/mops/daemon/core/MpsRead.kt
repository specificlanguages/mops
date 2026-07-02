package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget

interface MpsRead {
    fun list(target: List<String>?, depth: Int): MpsResult<MpsListEntryJson>

    fun getNode(target: NodeTarget): MpsResult<MpsNodeJson>

    fun findInstances(concept: String, exact: Boolean, limit: Int): MpsResult<FindInstancesPayload>

    fun findUsages(target: NodeTarget, limit: Int): MpsResult<FindUsagesPayload>
}
