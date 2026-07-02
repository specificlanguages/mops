package com.specificlanguages.mops.daemon.core

import com.specificlanguages.mops.protocol.ModelEditResponse
import com.specificlanguages.mops.protocol.EditBatch

interface MpsWrite : MpsRead {
    fun modelEdit(batch: EditBatch): MpsResult<ModelEditResponse>

    fun resave(modelTarget: String): MpsResult<Unit>
}
