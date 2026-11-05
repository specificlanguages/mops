package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Generation options for one make. A null field keeps the daemon's MPS generation settings for that option.
 *
 * - [threads]: generation thread count; 1 generates sequentially, a larger number in parallel within each model.
 * - [strict]: MPS strict generation mode. Parallel generation needs it.
 * - [messages]: which generator messages MPS reports.
 * - [saveTransientModels]: keep the intermediate models of each generation step.
 * - [performanceTracing]: report how long generation steps take.
 */
@Serializable
data class MakeGenerationOptions(
    val threads: Int? = null,
    val strict: Boolean? = null,
    val messages: MakeMessageLevel? = null,
    val saveTransientModels: Boolean? = null,
    val performanceTracing: PerformanceTracingLevel? = null,
)

/** The lowest severity of generator messages MPS reports during a make. */
@Serializable
enum class MakeMessageLevel {
    ERROR,
    WARNING,
    INFO,
}

/** How much detail the MPS generator's performance report has, from none to per-concept timings. */
@Serializable
enum class PerformanceTracingLevel {
    OFF,
    STEPS,
    LANGUAGES,
    TYPES,
}
