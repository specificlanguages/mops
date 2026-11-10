package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.Facade
import com.specificlanguages.mops.protocol.ConstraintMode
import com.specificlanguages.mops.protocol.ConstraintPolicy
import com.specificlanguages.mops.protocol.CreateModelRequest
import com.specificlanguages.mops.protocol.CreateModuleRequest
import com.specificlanguages.mops.protocol.EditBatch
import com.specificlanguages.mops.protocol.ModelCreationResponse
import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.ModuleCreationResponse

/**
 * Write operations on the MPS repository. Operations throw [MpsRequestException] on failures that
 * carry a specific error code.
 */
@Facade
interface MpsWrite : MpsRead {
    /**
     * Applies one batch of Edit Operations as a unit under a [ConstraintPolicy]. Atomicity is best-effort: a failure
     * before saving reverts the in-memory changes by reloading the affected models from disk, but saving is not
     * transactional, so a batch spanning several models can leave earlier models persisted if a later save fails.
     * Violations are always reported; [ConstraintPolicy] says whether they block the batch and whether a concept whose
     * language did not load fails it.
     */
    fun modelEdit(batch: EditBatch, constraints: ConstraintPolicy = ConstraintPolicy()): ModelEditResponse

    /**
     * Runs a **Module Creation Operation**, or only plans it when the request is a dry run. A created module and its
     * companions are saved before this returns.
     */
    fun createModule(request: CreateModuleRequest): ModuleCreationResponse

    /** Runs a **Model Creation Operation**. */
    fun createModel(request: CreateModelRequest): ModelCreationResponse
}
