package com.specificlanguages.mops.cli.check

/** How the check commands report their findings, for their help footers. */
internal const val CHECK_OUTPUT_HELP =
    "The check covers typesystem, model, module, and checking rules. Findings print most severe first. Text " +
        "output is one tab-separated row per finding (severity, message, and for a node-level finding the " +
        "node's name, concept, and reference), followed by `#` metadata lines: " +
        "`# checked <roots>: <errors>, <warnings>, <infos>` (or `# checked <roots>: no findings`) and, when --limit " +
        "cut the list, `# truncated: showing <shown> of <total> findings; pass --limit 0 for all`. A selection " +
        "with no root nodes has nothing to check: it prints an error and exits 1. See `mops explain output`."

internal const val CHECK_JSONL_HELP =
    "--format jsonl prints one finding object per line and ends with a summary record, {\"type\":\"summary\",...}, " +
        "carrying rootsChecked, shown, total, errors, warnings, infos, limit, truncated, and, when truncated, a " +
        "hint naming --limit 0. Findings have no type field. The totals and the exit code cover every finding, not only the " +
        "shown ones, in both formats."
