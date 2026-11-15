package com.specificlanguages.mops.cli.diagnose

import com.specificlanguages.mops.protocol.ModuleLoadFix

/** The reason codes both diagnose commands classify an unloaded module with, for their help footers. */
internal const val REASON_CODES_HELP =
    "Reason codes for an unloaded module: ABSENT (not in the repository), NOT_A_MODULE (resolves to something " +
        "that does not load classes), NO_JAVA_FACET (no Java facet; a defect for a language, informational for " +
        "another module), CLASSES_DISABLED (the Java facet is configured not to load classes), NOT_BUILT (classes " +
        "not generated or compiled yet), BROKEN_DEPENDENCIES (blocked by depended-on modules, reported " +
        "recursively), and RUNTIME_LOAD_FAILED (everything present but the runtime still did not register; " +
        "usually a class-link or version error in the daemon log)."

/** How both diagnose commands print the fix of a problem, for their help footers. */
internal const val FIX_HELP =
    "A problem with a known fix ends its row with a `fix=<command>` column. Only NOT_BUILT on a project module has " +
        "one: `mops make module <module>`. mops knows no fix for the other reasons, so their rows name the reason only. In " +
        "--json, a problem's `fix.makeModules` lists the modules to make."

/** The `fix=<command>` column of a problem row, or null when the problem has no known fix. */
internal fun fixColumn(fix: ModuleLoadFix?): String? = fix?.let { "fix=${it.command}" }
