package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class MopsBuildTest {
    @Test
    fun `version comes from the Gradle project version`() {
        assertEquals(System.getProperty("mops.expectedVersion"), MopsBuild.version)
    }
}
