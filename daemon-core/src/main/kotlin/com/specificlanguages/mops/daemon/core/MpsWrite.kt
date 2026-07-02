package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.EditApplyResponse
import com.specificlanguages.mops.protocol.EditBatch

interface MpsWrite : MpsRead {
    fun applyEdit(batch: EditBatch): MpsResult<EditApplyResponse>

    fun resave(modelTarget: String): MpsResult<Unit>
}
