package com.specificlanguages.mops.daemon

/** The observations used to decide that a model requires generation. */
data class ModelGenerationEvidence(
    val model: String,
    val source: String,
    val reason: String,
    val currentHash: String?,
    val recordedHash: String?,
    val generationRecord: String?,
    val currentHashChecked: Boolean,
    val recordedHashChecked: Boolean,
)
