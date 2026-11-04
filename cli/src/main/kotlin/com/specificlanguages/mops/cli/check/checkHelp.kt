package com.specificlanguages.mops.cli.check

/** How the check commands report their findings, for their help footers. */
internal const val CHECK_OUTPUT_HELP =
    "Findings print most severe first. Text output is one tab-separated row per finding (severity, message, and " +
        "for a node-level finding the node's name, concept, and reference), followed by `#` metadata lines: " +
        "`# <errors>, <warnings>, <infos>` (or `# no findings`) and, when --limit cut the list, " +
        "`# truncated: showing <shown> of <total> findings; pass --limit 0 for all`. See `mops explain output`."

internal const val CHECK_JSONL_HELP =
    "--format jsonl prints one finding object per line and ends with a {\"type\":\"summary\",...} record carrying " +
        "shown, total, errors, warnings, infos, limit, truncated, and, when truncated, a hint naming --limit 0. " +
        "Findings have no type field. The totals and the exit code cover every finding, not only the shown ones, " +
        "in both formats."
