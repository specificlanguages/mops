package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.FindByNameResponse
import com.specificlanguages.mops.protocol.FindInstancesResponse
import com.specificlanguages.mops.protocol.FindUsagesResponse
import com.specificlanguages.mops.protocol.MpsListEntryJson
import com.specificlanguages.mops.protocol.MpsNodeJson
import com.specificlanguages.mops.protocol.NodeTarget

/**
 * Read operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsRead {
    fun list(target: List<String>?, depth: Int): MpsListEntryJson

    fun getNode(target: NodeTarget): MpsNodeJson

    fun findInstances(concept: String, exact: Boolean, limit: Int, all: Boolean = false): FindInstancesResponse

    fun findByName(pattern: String, limit: Int, all: Boolean = false): FindByNameResponse

    fun findUsages(target: NodeTarget, limit: Int, all: Boolean = false): FindUsagesResponse
}
