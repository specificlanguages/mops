package com.specificlanguages.mops.daemon

/** Why a project language runtime is unavailable or may not match its sources. */
enum class LanguageUnusableReason(val explanation: String) {
    RUNTIME_UNAVAILABLE("runtime is not loaded"),
    GENERATION_REQUIRED("generated output may not match current sources"),
}
