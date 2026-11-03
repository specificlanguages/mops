package com.specificlanguages.mops.cli.common

/**
 * Path macros MPS expands itself in project files (`jetbrains.mps.util.MacrosFactory`). `$PROJECT_DIR$` does not use
 * the `${...}` form and is not listed.
 */
internal val mpsBuiltInPathMacros = setOf("project", "mps_home", "platform_lib", "lib_ext")
