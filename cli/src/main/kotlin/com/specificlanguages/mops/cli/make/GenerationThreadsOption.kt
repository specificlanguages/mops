package com.specificlanguages.mops.cli.make

import picocli.CommandLine.ITypeConverter
import picocli.CommandLine.Option
import picocli.CommandLine.TypeConversionException

/**
 * The `--threads` option of the make commands: the number of threads MPS generates with during this make only.
 */
class GenerationThreadsOption {
    @Option(
        names = ["--threads"],
        paramLabel = "N",
        converter = [ThreadCountConverter::class],
        description = [
            "Generate with N threads during this make; 1 generates sequentially. Without it, the make uses the " +
                "daemon's MPS generation settings (MPS's default is sequential).",
        ],
    )
    var threads: Int? = null

    /** Converts the `--threads` value, rejecting anything that is not a whole number of at least 1. */
    class ThreadCountConverter : ITypeConverter<Int> {
        override fun convert(value: String): Int {
            val threads = value.toIntOrNull() ?: throw TypeConversionException("$value (must be a whole number)")
            if (threads < 1) throw TypeConversionException("$value (must be at least 1)")
            return threads
        }
    }
}
