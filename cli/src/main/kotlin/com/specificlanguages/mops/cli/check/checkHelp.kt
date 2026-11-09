package com.specificlanguages.mops.cli.check

/** How the check commands report their findings, for their help footers. */
internal const val CHECK_OUTPUT_HELP =
    "The check covers typesystem, model, module, and checking rules. Findings print most severe first. Text " +
        "output is one tab-separated row per finding (severity, message, and for a node-level finding the " +
        "node's name, concept, and reference), followed by `#` metadata lines: " +
        "`# checked <roots>: <errors>, <warnings>, <infos>` (or `# checked <roots>: no findings`) and, when --limit " +
        "cut the list, `# truncated: showing <shown> of <total> findings; pass --limit 0 for all`. " +
        "See `mops explain output`."

internal const val CHECK_JSONL_HELP =
    "--format jsonl prints one finding object per line and ends with a summary record, {\"type\":\"summary\",...}, " +
        "with a field for every summary fact; the record itself is the full list. Read total and errors for the full " +
        "result and truncated for whether --limit cut the list; a truncated record also has a hint naming " +
        "--limit 0. Findings have no type field."

internal const val CHECK_EXIT_HELP =
    "The totals and the exit code cover every finding, not only the shown ones, in both formats. The command exits " +
        "1 when any finding is an error and 0 for warnings and infos alone. A selection with no root nodes has " +
        "nothing to check: the command prints an error to stderr, no findings or summary, and exits 1."
