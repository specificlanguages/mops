package com.specificlanguages.mops.cli.diagnose

/** The reason codes both diagnose commands classify an unloaded module with, for their help footers. */
internal const val REASON_CODES_HELP =
    "Reason codes for an unloaded module: ABSENT (not in the repository), NOT_A_MODULE (resolves to something " +
        "that does not load classes), NO_JAVA_FACET (no Java facet; a defect for a language, informational for " +
        "another module), CLASSES_DISABLED (the Java facet is configured not to load classes), NOT_BUILT (classes " +
        "not generated or compiled yet), BROKEN_DEPENDENCIES (blocked by depended-on modules, reported " +
        "recursively), and RUNTIME_LOAD_FAILED (everything present but the runtime still did not register; " +
        "usually a class-link or version error in the daemon log)."
