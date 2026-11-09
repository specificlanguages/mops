package com.specificlanguages.mops.cli.find

/** The output of the `find` subcommands that return nodes, for their help footers. */
internal const val NODE_RESULTS_HELP =
    "Text output is one tab-separated row per node: `root` or `node`, the node name (or <unnamed>), the node's " +
        "concept, and its serialized reference. A non-root node appends `parent` and the parent's name (or " +
        "<unnamed>), concept, and reference. --json prints an object with `target` (the query as mops resolved it), " +
        "`scope`, `limit`, `truncated`, and a `nodes` array whose non-root entries carry a nested `parent` summary. " +
        "See `mops explain output`."
