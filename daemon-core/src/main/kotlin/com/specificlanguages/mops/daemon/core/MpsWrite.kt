package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.ConstraintEnforcement
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch

/**
 * Write operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
interface MpsWrite : MpsRead {
    /**
     * Applies one atomic batch of Edit Operations under a [ConstraintEnforcement] policy. Violations are always
     * reported. [ConstraintEnforcement.BEST_EFFORT] blocks the batch on any violation (nothing is saved); a concept
     * whose language did not load is skipped and reported as a warning. [ConstraintEnforcement.ADVISORY] applies and
     * saves the batch even with violations. [ConstraintEnforcement.STRICT] additionally aborts when a concept cannot
     * be checked because its language did not load.
     */
    fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT): ModelEditResponse

    fun resave(modelTarget: String)
}
