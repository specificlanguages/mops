package com.specificlanguages.mops.cli.check

import java.util.Locale
import picocli.CommandLine.ITypeConverter

enum class CheckOutputFormat {
    HUMAN,
    JSONL;

    class Converter : ITypeConverter<CheckOutputFormat> {
        override fun convert(value: String): CheckOutputFormat =
            entries.find { it.name.lowercase(Locale.ROOT) == value.lowercase(Locale.ROOT) }
                ?: throw IllegalArgumentException("unknown --format value '$value'; expected human or jsonl")
    }
}
