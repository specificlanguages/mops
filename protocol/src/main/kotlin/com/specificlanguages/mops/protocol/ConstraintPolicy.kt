package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * How `mops edit model` treats **Constraints**: [mode] decides what a violation does, and [ignoreMissingLanguages]
 * decides what a concept that cannot be checked because its language did not load does. When [ignoreMissingLanguages]
 * is false, such a concept fails the batch; when true, it is skipped and reported as a warning. The two settings are
 * independent.
 */
@Serializable
data class ConstraintPolicy(
    val mode: ConstraintMode = ConstraintMode.STRICT,
    val ignoreMissingLanguages: Boolean = false,
)
