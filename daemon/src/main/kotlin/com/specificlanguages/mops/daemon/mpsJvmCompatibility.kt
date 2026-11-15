package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.launcher.MpsBuildProperties

data class EnvironmentProblem(val code: String, val message: String)

/**
 * Logic. The reason the daemon JVM cannot run the MPS distribution described by [mpsBuildProperties], or null when it
 * can. [mpsBuildProperties] is null when the distribution has no readable `build.properties`. [javaMajor] and
 * [javaVendor] describe the running JVM.
 *
 * Hides: which JVMs a given MPS version accepts.
 */
fun jvmCompatibilityProblem(mpsBuildProperties: MpsBuildProperties?, javaMajor: Int, javaVendor: String): EnvironmentProblem? {
    if (mpsBuildProperties == null) {
        return EnvironmentProblem(
            "MPS_HOME_INVALID",
            "MPS home directory does not contain build.properties file"
        )
    }

    val mpsBuildNumber = mpsBuildProperties.buildNumber
    val mpsVersion = mpsBuildProperties.version()
        ?: return EnvironmentProblem(
            "MPS_VERSION_UNKNOWN",
            "could not find out MPS version from the build.properties file (build number: $mpsBuildNumber)"
        )
    val requiredJavaMajor = mpsVersion.requiredJavaMajor()

    if (javaMajor != requiredJavaMajor) {
        return EnvironmentProblem(
            code = "JVM_VERSION_MISMATCH",
            message = "daemon JVM $javaMajor is not compatible with MPS $mpsBuildNumber; required Java $requiredJavaMajor",
        )
    }

    if (!javaVendor.contains("JetBrains", ignoreCase = true)) {
        return EnvironmentProblem(
            code = "JVM_VENDOR_UNSUPPORTED",
            message = "daemon JVM is not a JetBrains Runtime; current vendor is '$javaVendor'",
        )
    }
    return null
}
