package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch

/**
 * Write operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsWrite : MpsRead {
    /**
     * Applies one atomic batch of Edit Operations. When [force] is false, any Constraint Violation blocks the whole
     * batch: nothing is saved and the loaded model is left unchanged, but the violations are still reported. When
     * [force] is true, the batch is applied and saved even if it produces violations, which are still reported.
     */
    fun modelEdit(batch: EditBatch, force: Boolean = false): ModelEditResponse

    fun resave(modelTarget: String)
}
