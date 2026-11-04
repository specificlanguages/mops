package com.specificlanguages.mops.cli.make

/** How both make commands report their result, for their help footers. */
internal const val MAKE_OUTPUT_HELP =
    "Text output prints the make messages as `<kind><TAB><text>` lines on stderr, since they are diagnostics, and " +
        "a one-line action report on stdout: `made <N> module(s): success`, `made <N> module(s): FAILED (<N> " +
        "error(s))`, or `nothing to generate`. The command exits non-zero when the make reports errors."
