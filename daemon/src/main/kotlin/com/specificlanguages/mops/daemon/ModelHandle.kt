package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

class ModelHandle(val sModel: SModel, val module: ModuleHandle) {
    val modelName get() = sModel.name.value
    val modelReference get() = PersistenceFacade.getInstance().asString(sModel.reference)
}
