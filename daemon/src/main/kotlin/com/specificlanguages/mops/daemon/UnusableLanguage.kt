package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLoadProblemJson

/** A language refusal and the observations that justify it. */
data class UnusableLanguage(
    val name: String,
    val reason: LanguageUnusableReason,
    val models: List<ModelGenerationEvidence> = emptyList(),
    val loadProblem: ModuleLoadProblemJson? = null,
) {
    val explanation: String
        get() = when (reason) {
            LanguageUnusableReason.RUNTIME_UNAVAILABLE -> "runtime is not loaded"
            LanguageUnusableReason.GENERATION_REQUIRED -> "generated output may not match current sources"
        }
}
