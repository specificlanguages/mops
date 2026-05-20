package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.launcher.MpsBuildProperties
import java.nio.file.Path

data class EnvironmentProblem(val code: String, val message: String)

fun checkCurrentJvm(mpsHome: Path): EnvironmentProblem? {
    val mpsBuildProperties = MpsBuildProperties.fromMpsHome(mpsHome)
        ?: return EnvironmentProblem(
            "MPS_HOME_INVALID",
            "MPS home directory does not contain build.properties file"
        )

    val mpsBuildNumber = mpsBuildProperties.buildNumber
    val mpsVersion = mpsBuildProperties.version()
        ?: return EnvironmentProblem(
            "MPS_VERSION_UNKNOWN",
            "could not find out MPS version from the build.properties file (build number: $mpsBuildNumber)"
        )
    val requiredJavaMajor = mpsVersion.requiredJavaMajor()

    val currentJavaMajor = Runtime.version().feature()
    if (currentJavaMajor != requiredJavaMajor) {
        return EnvironmentProblem(
            code = "JVM_VERSION_MISMATCH",
            message = "daemon JVM $currentJavaMajor is not compatible with MPS $mpsBuildNumber; required Java $requiredJavaMajor",
        )
    }

    val currentVendor = System.getProperty("java.vendor", "")
    if (!currentVendor.contains("JetBrains", ignoreCase = true)) {
        return EnvironmentProblem(
            code = "JVM_VENDOR_UNSUPPORTED",
            message = "daemon JVM is not a JetBrains Runtime; current vendor is '$currentVendor'",
        )
    }
    return null
}
