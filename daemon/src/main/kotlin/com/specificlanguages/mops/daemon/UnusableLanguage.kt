package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadProblemJson

/** A language refusal and the observations that justify it. */
data class UnusableLanguage(
    val name: String,
    val reason: LanguageUnusableReason,
    val models: List<ModelGenerationEvidence> = emptyList(),
    val loadProblem: ModuleLoadProblemJson? = null,
)
