package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
import jetbrains.mps.generator.GenerationOptions
import jetbrains.mps.generator.IGenerationSettings
import jetbrains.mps.generator.IModifiableGenerationSettings
import jetbrains.mps.internal.make.cfg.GenerateFacetInitializer
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.script.IScriptController

/**
 * The generation options for one make, built from [settings] so the options the request leaves out carry over, or null
 * when the request sets none of the options that travel with the make session ([MakeGenerationOptions.threads],
 * [MakeGenerationOptions.strict], [MakeGenerationOptions.messages]); the Generate facet then reads the settings itself.
 * A thread count of 1 generates sequentially.
 *
 * Generation options passed through [GenerateFacetInitializer] apply to the one make session whose script controller
 * populates them; the Generate facet falls back to the settings only when no options are set. MPS generates in
 * parallel only in strict mode, so a parallel request fails instead of silently generating sequentially when strict
 * mode is off.
 *
 * See https://github.com/specificlanguages/mps-api-research/blob/main/docs/generation-options-per-make.md.
 */
internal fun makeGenerationOptions(
    settings: IGenerationSettings,
    request: MakeGenerationOptions,
): GenerationOptions.OptionsBuilder? {
    if (request.threads == null && request.strict == null && request.messages == null) return null
    val threads = request.threads
    val strict = request.strict ?: settings.isStrictMode
    if (threads != null) requireValidThreadCount(threads, strict, strictFromRequest = request.strict != null)
    val options = GenerationOptions.fromSettings(settings).strictMode(strict)
    if (threads != null) options.generateInParallel(threads > 1, threads)
    request.messages?.let { level ->
        val showWarnings = level != MakeMessageLevel.ERROR
        options.reporting(
            level == MakeMessageLevel.INFO,
            showWarnings,
            settings.isKeepModelsWithWarnings,
            settings.numberOfModelsToKeep,
        )
        // fromSettings derives these two from the warnings setting in the same way.
        options.showBadChildWarning(showWarnings && settings.isShowBadChildWarning)
        options.warnDynamicToStatic(showWarnings && settings.warnDynamicToStaticReference())
    }
    return options
}

/**
 * Fails with [MpsErrorCode.INVALID_REQUEST] when [threads] is below 1, or above 1 while [strict] mode is off: MPS
 * generates in parallel only in strict mode and would otherwise generate sequentially without saying so.
 */
private fun requireValidThreadCount(threads: Int, strict: Boolean, strictFromRequest: Boolean) {
    if (threads < 1) {
        throw MpsRequestException(
            MpsErrorCode.INVALID_REQUEST,
            "generation thread count must be at least 1, got $threads",
        )
    }
    if (threads > 1 && !strict) {
        val reason = if (strictFromRequest) "the request turns it off" else "the generation settings turn it off"
        throw MpsRequestException(
            MpsErrorCode.INVALID_REQUEST,
            "parallel generation needs strict mode, but $reason; use --strict",
        )
    }
}

/**
 * The script controller for one make [session]. With [options], it hands them to the Generate facet of that session's
 * scripts; without, the facet builds its options from the generation settings.
 */
internal fun makeScriptController(session: MakeSession, options: GenerationOptions.OptionsBuilder?): IScriptController =
    if (options == null) {
        IScriptController.Stub2(session)
    } else {
        IScriptController.Stub2(session, GenerateFacetInitializer().setGenerationOptions(options))
    }

/**
 * Runs [make] with the request's [MakeGenerationOptions.saveTransientModels] and
 * [MakeGenerationOptions.performanceTracing] set in [settings], and restores the previous values afterwards, also when
 * [make] fails. The Generate facet reads these two from the generation settings and overwrites any value the make
 * session passes, so they cannot travel with the session like the other options. The settings are process-wide, so
 * this relies on makes not running concurrently.
 */
internal fun <T> withMakeGenerationSettings(
    settings: IModifiableGenerationSettings,
    request: MakeGenerationOptions,
    make: () -> T,
): T {
    val savedTransientModels = settings.isSaveTransientModels
    val savedTracing = settings.performanceTracingLevel
    try {
        request.saveTransientModels?.let { settings.isSaveTransientModels = it }
        request.performanceTracing?.let { settings.performanceTracingLevel = it.mpsTracingMode }
        return make()
    } finally {
        if (request.saveTransientModels != null) settings.isSaveTransientModels = savedTransientModels
        if (request.performanceTracing != null) settings.performanceTracingLevel = savedTracing
    }
}

private val PerformanceTracingLevel.mpsTracingMode: Int
    get() = when (this) {
        PerformanceTracingLevel.OFF -> GenerationOptions.TRACE_OFF
        PerformanceTracingLevel.STEPS -> GenerationOptions.TRACE_STEPS
        PerformanceTracingLevel.LANGUAGES -> GenerationOptions.TRACE_LANGS
        PerformanceTracingLevel.TYPES -> GenerationOptions.TRACE_TYPES
    }
