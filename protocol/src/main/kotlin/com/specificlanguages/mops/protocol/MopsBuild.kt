package com.specificlanguages.mops.protocol

import java.util.Properties

/** Build information that Gradle writes into `mops-version.properties` at build time. */
object MopsBuild {
    private val properties: Properties by lazy {
        val properties = Properties()
        val resource = MopsBuild::class.java.getResourceAsStream("/mops-version.properties")
            ?: error("mops-version.properties is missing from the runtime classpath")
        resource.use(properties::load)
        properties
    }

    /** The project version from `gradle.properties`, for example `0.4.0-SNAPSHOT`. */
    val version: String by lazy {
        properties.getProperty("version") ?: error("version is missing from mops-version.properties")
    }

    /**
     * The abbreviated git commit the build was made from, with a `-dirty` suffix when the working tree had uncommitted
     * changes, or `null` when the build had no git metadata.
     */
    val revision: String? by lazy { properties.getProperty("revision")?.takeIf { it.isNotBlank() } }

    /** The version and the git revision, for example `0.4.0-SNAPSHOT (abc1234)`. */
    val fullVersion: String by lazy { fullVersion(version, revision) }

    fun fullVersion(version: String, revision: String?): String = "$version (${revision ?: "unknown revision"})"
}
