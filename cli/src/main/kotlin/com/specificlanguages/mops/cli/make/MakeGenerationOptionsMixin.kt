package com.specificlanguages.mops.cli.make

import com.specificlanguages.mops.protocol.MakeGenerationOptions
import com.specificlanguages.mops.protocol.MakeMessageLevel
import com.specificlanguages.mops.protocol.PerformanceTracingLevel
import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.TypeConversionException

/**
 * The generation options of both make commands. Each applies to this make only; an option left out keeps the daemon's
 * MPS generation settings.
 */
class MakeGenerationOptionsMixin {
    @Option(
        names = ["--threads"],
        paramLabel = "N",
        converter = [ThreadCountConverter::class],
        description = [
            "Generate with N threads; 1 generates sequentially. MPS's default is sequential.",
        ],
    )
    var threads: Int? = null

    @Option(
        names = ["--strict"],
        negatable = true,
        description = [
            "Generate in MPS strict mode, or not with --no-strict. MPS's default is strict.",
        ],
    )
    var strict: Boolean? = null

    @Option(
        names = ["--messages"],
        paramLabel = "LEVEL",
        converter = [MessageLevelConverter::class],
        description = [
            "The lowest severity of messages the result has, from every make step: error, warning, or info. " +
                "Without it, the result has errors and warnings but no info messages.",
        ],
    )
    var messages: MakeMessageLevel? = null

    @Option(
        names = ["--save-transient-models"],
        negatable = true,
        description = ["Keep the intermediate models of each generation step, or not with --no-save-transient-models."],
    )
    var saveTransientModels: Boolean? = null

    @Option(
        names = ["--performance-tracing"],
        paramLabel = "LEVEL",
        converter = [TracingLevelConverter::class],
        description = [
            "Report how long generation takes: off, steps, languages, or types (most detail). The report comes " +
                "as info messages, so combine it with --messages info.",
        ],
    )
    var performanceTracing: PerformanceTracingLevel? = null

    /** The options as a make request carries them. */
    fun toProtocol(): MakeGenerationOptions =
        MakeGenerationOptions(threads, strict, messages, saveTransientModels, performanceTracing)

    /** Converts the `--threads` value, rejecting anything that is not a whole number of at least 1. */
    class ThreadCountConverter : ITypeConverter<Int> {
        override fun convert(value: String): Int {
            val threads = value.toIntOrNull() ?: throw TypeConversionException("$value (must be a whole number)")
            if (threads < 1) throw TypeConversionException("$value (must be at least 1)")
            return threads
        }
    }

    /** Converts the lower-case `--messages` value. */
    class MessageLevelConverter : ITypeConverter<MakeMessageLevel> {
        override fun convert(value: String): MakeMessageLevel = lowerCaseEnum(value)
    }

    /** Converts the lower-case `--performance-tracing` value. */
    class TracingLevelConverter : ITypeConverter<PerformanceTracingLevel> {
        override fun convert(value: String): PerformanceTracingLevel = lowerCaseEnum(value)
    }
}

private inline fun <reified E : Enum<E>> lowerCaseEnum(value: String): E =
    enumValues<E>().firstOrNull { it.name.lowercase() == value }
        ?: throw TypeConversionException(
            "$value (expected one of ${enumValues<E>().joinToString(", ") { it.name.lowercase() }})",
        )
