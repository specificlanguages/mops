package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadFix
import com.specificlanguages.mops.protocol.ModuleLoadProblemJson

/** A language refusal, the observations that justify it, and the [fix] that makes the language usable, when known. */
data class UnusableLanguage(
    val name: String,
    val reason: LanguageUnusableReason,
    val models: List<ModelGenerationEvidence> = emptyList(),
    val loadProblem: ModuleLoadProblemJson? = null,
    val fix: ModuleLoadFix? = null,
)
