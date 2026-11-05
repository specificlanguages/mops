package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.Facade
import com.specificlanguages.mops.protocol.ConstraintMode
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
     * Applies one batch of Edit Operations as a unit. Atomicity is best-effort: a failure before saving reverts the
     * in-memory changes by reloading the affected models from disk, but saving is not transactional, so a batch spanning
     * several models can leave earlier models persisted if a later save fails.
     *
     * Violations are always reported. [ConstraintMode.STRICT] blocks the batch on any violation (nothing is saved);
     * [ConstraintMode.ADVISORY] applies and saves the batch anyway. A concept that cannot be checked because its
     * language did not load fails the batch, unless [ignoreMissingLanguages] is set: then it is skipped and reported as
     * a warning.
     */
    fun modelEdit(
        batch: EditBatch,
        constraints: ConstraintMode = ConstraintMode.STRICT,
        ignoreMissingLanguages: Boolean = false,
    ): ModelEditResponse

    /**
     * Runs a **Module Creation Operation**, or only plans it when the request is a dry run. A created module and its
     * companions are saved before this returns.
     */
    fun createModule(request: CreateModuleRequest): ModuleCreationResponse

    /** Runs a **Model Creation Operation**. */
    fun createModel(request: CreateModelRequest): ModelCreationResponse
}
