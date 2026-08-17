package com.specificlanguages.mops.protocol

import java.util.Properties

object MopsBuild {
    val version: String by lazy {
        val properties = Properties()
        val resource = MopsBuild::class.java.getResourceAsStream("/mops-version.properties")
            ?: error("mops-version.properties is missing from the runtime classpath")
        resource.use(properties::load)
        properties.getProperty("version") ?: error("version is missing from mops-version.properties")
    }
}
