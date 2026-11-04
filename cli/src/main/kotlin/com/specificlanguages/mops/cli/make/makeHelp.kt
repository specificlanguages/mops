package com.specificlanguages.mops.cli.make

/** How both make commands report their result, for their help footers. */
internal const val MAKE_OUTPUT_HELP =
    "Text output prints the make messages as `<kind><TAB><text>` lines on stderr, since they are diagnostics, and " +
        "a one-line action report on stdout: `made <N> module(s): success`, `made <N> module(s): FAILED (<N> " +
        "error(s))`, or `nothing to generate`. The command exits non-zero when the make reports errors."

/** What `--threads` speeds up, for the help footers of both make commands. */
internal const val MAKE_THREADS_HELP =
    "With `--threads`, MPS generates in parallel within each model (it applies template rules in parallel), not " +
        "across models or modules, so the gain depends on the size of the models."
