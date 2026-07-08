package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/**
 * Why one module could not be loaded, as a node in a dependency tree. [reason] is a stable machine code:
 * - `ABSENT` - the module is not present in the repository.
 * - `NOT_A_MODULE` - the reference resolves to something that is not a class-loadable module.
 * - `NO_JAVA_FACET` - the module has no Java facet. For a language this is a defect (a language must ship classes);
 *   for another module it just means it contributes no Java classes.
 * - `CLASSES_DISABLED` - the Java facet is configured not to load classes into MPS.
 * - `NOT_BUILT` - the module should have classes but they have not been generated/compiled yet.
 * - `BROKEN_DEPENDENCIES` - the module itself is fine but modules it depends on are not loaded; [causes] holds their
 *   problems (recursively), so the leaves of the tree are the root modules to fix.
 * - `RUNTIME_LOAD_FAILED` - classes and dependencies are all in place, yet the runtime did not register; usually a
 *   class-link or version error visible in the daemon log.
 *
 * [causes] is populated only for `BROKEN_DEPENDENCIES`.
 */
@Serializable
data class ModuleLoadProblemJson(
    val module: String,
    val reason: String,
    val detail: String? = null,
    val causes: List<ModuleLoadProblemJson> = emptyList(),
)
