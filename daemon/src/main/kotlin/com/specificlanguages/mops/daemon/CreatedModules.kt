package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.module.SModule

data class CreatedModules(
    val primary: SModule,
    val companions: List<SModule> = emptyList(),
)
