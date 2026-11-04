package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import jetbrains.mps.generator.GenerationOptions
import jetbrains.mps.generator.IGenerationSettings
import jetbrains.mps.internal.make.cfg.GenerateFacetInitializer
import jetbrains.mps.make.MakeSession
import jetbrains.mps.make.script.IScriptController

/**
 * The generation options for one make that generates with [threads] threads, built from [settings] so the other
 * generation settings carry over, or null when [threads] is null and the Generate facet reads the settings itself.
 * A thread count of 1 generates sequentially.
 *
 * Generation options passed through [GenerateFacetInitializer] apply to the one make session whose script controller
 * populates them; the Generate facet falls back to the settings only when no options are set. MPS generates in
 * parallel only in strict mode, so a parallel request fails instead of silently generating sequentially when the
 * settings turn strict mode off.
 */
internal fun makeGenerationOptions(settings: IGenerationSettings, threads: Int?): GenerationOptions.OptionsBuilder? {
    if (threads == null) return null
    if (threads < 1) {
        throw MpsRequestException(MpsErrorCode.INVALID_REQUEST, "generation thread count must be at least 1, got $threads")
    }
    if (threads > 1 && !settings.isStrictMode) {
        throw MpsRequestException(
            MpsErrorCode.INVALID_REQUEST,
            "parallel generation needs strict mode, which the generation settings turn off",
        )
    }
    return GenerationOptions.fromSettings(settings).generateInParallel(threads > 1, threads)
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
