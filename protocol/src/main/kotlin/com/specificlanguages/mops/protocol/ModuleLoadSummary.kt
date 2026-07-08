package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Counts of how many of the diagnosed modules have their classes loaded, carried in a [ModulesDiagnosticsResponse]. A
 * module is loaded when its runtime is registered in MPS; only loaded languages contribute concepts to name-based
 * concept lookup, so an unloaded language is why `find instances` cannot resolve one of its concepts.
 */
@Serializable
data class ModuleLoadSummary(
    val total: Int,
    val loaded: Int,
    val failed: Int,
)
