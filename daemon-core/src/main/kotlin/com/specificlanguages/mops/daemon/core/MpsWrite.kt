package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch

/**
 * Write operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsWrite : MpsRead {
    fun modelEdit(batch: EditBatch): ModelEditResponse

    fun resave(modelTarget: String)
}
