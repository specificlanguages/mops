package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable

/** Why one module could not be loaded. The wire spelling is the constant name. */
@Serializable
enum class ModuleLoadProblemReason {
    /** The module is not present in the repository. */
    ABSENT,

    /** The reference resolves to something that is not a class-loadable module. */
    NOT_A_MODULE,

    /**
     * The module has no Java facet. For a language this is a defect (a language must ship classes); for another
     * module it just means it contributes no Java classes.
     */
    NO_JAVA_FACET,

    /** The Java facet is configured not to load classes into MPS. */
    CLASSES_DISABLED,

    /** The module should have classes but they have not been generated and compiled yet. */
    NOT_BUILT,

    /**
     * The module itself is fine but modules it depends on are not loaded; [ModuleLoadProblemJson.causes] holds their
     * problems.
     */
    BROKEN_DEPENDENCIES,

    /**
     * Classes and dependencies are all in place, yet the runtime did not register; usually a class-link or version
     * error visible in the daemon log.
     */
    RUNTIME_LOAD_FAILED,
}
