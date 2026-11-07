package com.specificlanguages.mops.cli.make

/** How both make commands report their result, for their help footers. */
internal const val MAKE_OUTPUT_HELP =
    "Text output prints the make messages as `<kind><TAB><text>` lines on stderr, since they are diagnostics, and " +
        "a one-line action report on stdout: `made <N> module(s): success`, `made <N> module(s): FAILED (<N> " +
        "error(s))`, or `nothing to generate`. The command exits non-zero when the make reports errors."

/** What a make deletes, for the help footers of both make commands. */
internal const val MAKE_CLEAN_HELP =
    "The make generates every model of the made modules and deletes files in their output folders that it no " +
        "longer generates."

/** How the generation options behave, for the help footers of both make commands. */
internal const val MAKE_GENERATION_HELP =
    "The generation options apply to this make only; without them the make uses the daemon's MPS generation " +
        "settings. With `--threads`, MPS generates in parallel within each model (it applies template rules in " +
        "parallel), not across models or modules, so the gain depends on the size of the models. MPS generates in " +
        "parallel only in strict mode, so with N above 1 the make fails when strict mode is off; `--strict` turns " +
        "it on. Saved transient models stay in the daemon until the next make."
