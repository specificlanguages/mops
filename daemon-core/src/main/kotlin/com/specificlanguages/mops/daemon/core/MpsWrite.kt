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
     * Applies one batch of Edit Operations as a unit under a [ConstraintEnforcement] policy. Atomicity is best-effort:
     * a failure before saving reverts the in-memory changes by reloading the affected models from disk, but saving is
     * not transactional, so a batch spanning several models can leave earlier models persisted if a later save fails.
     * Violations are always reported. [ConstraintEnforcement.BEST_EFFORT] blocks the batch on any violation (nothing is
     * saved); a concept whose language did not load is skipped and reported as a warning. [ConstraintEnforcement.ADVISORY]
     * applies and saves the batch even with violations. [ConstraintEnforcement.STRICT] additionally aborts when a concept
     * cannot be checked because its language did not load.
     */
    fun modelEdit(batch: EditBatch, constraints: ConstraintEnforcement = ConstraintEnforcement.BEST_EFFORT): ModelEditResponse
}
