package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.launcher.MpsBuildProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmCompatibilityProblemTest {
    private val mps20253 = MpsBuildProperties("MPS-253.1234")

    @Test
    fun `a JetBrains Runtime of the required version is compatible`() {
        assertNull(jvmCompatibilityProblem(mps20253, javaMajor = 21, javaVendor = "JetBrains s.r.o."))
    }

    @Test
    fun `a distribution without build properties is invalid`() {
        assertEquals("MPS_HOME_INVALID", jvmCompatibilityProblem(null, 21, "JetBrains s.r.o.")?.code)
    }

    @Test
    fun `an unparsable build number is an unknown version`() {
        assertEquals("MPS_VERSION_UNKNOWN", jvmCompatibilityProblem(MpsBuildProperties("snapshot"), 21, "JetBrains s.r.o.")?.code)
    }

    @Test
    fun `a JVM of another major version is a version mismatch`() {
        assertEquals(
            EnvironmentProblem("JVM_VERSION_MISMATCH", "daemon JVM 17 is not compatible with MPS MPS-253.1234; required Java 21"),
            jvmCompatibilityProblem(mps20253, javaMajor = 17, javaVendor = "JetBrains s.r.o."),
        )
    }

    @Test
    fun `a JVM from another vendor is unsupported`() {
        assertEquals(
            EnvironmentProblem("JVM_VENDOR_UNSUPPORTED", "daemon JVM is not a JetBrains Runtime; current vendor is 'Eclipse Adoptium'"),
            jvmCompatibilityProblem(mps20253, javaMajor = 21, javaVendor = "Eclipse Adoptium"),
        )
    }
}
