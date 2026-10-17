package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Every module in this repository whose name or serialized module reference equals [target]. The caller decides what
 * zero or several matches mean; nothing here picks among them.
 */
internal fun SRepository.modulesMatching(target: String): List<SModule> {
    val persistence = PersistenceFacade.getInstance()
    return modules.filter { it.moduleName == target || persistence.asString(it.moduleReference) == target }
}
