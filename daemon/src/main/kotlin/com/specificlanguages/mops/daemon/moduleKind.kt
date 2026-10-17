package com.specificlanguages.mops.daemon

import jetbrains.mps.project.DevKit
import jetbrains.mps.project.Solution
import jetbrains.mps.smodel.Generator
import jetbrains.mps.smodel.Language
import org.jetbrains.mps.openapi.module.SModule

/** The module kind name mops reports: `language`, `generator`, `devkit`, `solution`, or `other`. */
internal fun SModule.moduleKind(): String =
    when (this) {
        is Language -> "language"
        is Generator -> "generator"
        is DevKit -> "devkit"
        is Solution -> "solution"
        else -> "other"
    }
