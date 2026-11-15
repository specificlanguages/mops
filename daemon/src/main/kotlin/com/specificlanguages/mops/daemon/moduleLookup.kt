package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Logic. The modules among these whose name or serialized module reference equals [target].
 *
 * Hides: what text addresses a module. Every command, Code Mode lookup, and edit path that resolves a module target
 * matches through this function. The caller decides which modules are candidates (the repository or the Project
 * Modules) and what zero or several matches mean; nothing here picks among them.
 */
internal fun Iterable<SModule>.matchingTarget(target: String): List<SModule> {
    val persistence = PersistenceFacade.getInstance()
    return filter { it.moduleName == target || persistence.asString(it.moduleReference) == target }
}
