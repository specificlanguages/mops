package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MopsBuildTest {
    @Test
    fun `version comes from the Gradle project version`() {
        assertEquals(System.getProperty("mops.expectedVersion"), MopsBuild.version)
    }

    @Test
    fun `full version names the build version and the git revision`() {
        assertEquals("0.4.0-SNAPSHOT (abc1234)", MopsBuild.fullVersion("0.4.0-SNAPSHOT", "abc1234"))
        assertEquals("0.4.0-SNAPSHOT (abc1234-dirty)", MopsBuild.fullVersion("0.4.0-SNAPSHOT", "abc1234-dirty"))
    }

    @Test
    fun `full version says when the git revision is unknown`() {
        assertEquals("0.4.0 (unknown revision)", MopsBuild.fullVersion("0.4.0", null))
    }

    @Test
    fun `build reports its own full version`() {
        assertTrue(MopsBuild.fullVersion.startsWith("${MopsBuild.version} ("), MopsBuild.fullVersion)
    }
}
