package com.specificlanguages.mops.cli

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ModelGetNodeCommandTest {
    @Test
    fun `model get-node requires a node reference or model target plus node id`() {
        val stderr = ByteArrayOutputStream()

        val exitCode = newCommandLine().also {
            it.err = PrintWriter(stderr, true)
        }.execute("model", "get-node")

        assertEquals(2, exitCode)
        assertContains(stderr.toString(), "Missing required parameter")
        assertContains(stderr.toString(), "NODE_REFERENCE")
        assertContains(stderr.toString(), "MODEL_TARGET")
        assertContains(stderr.toString(), "NODE_ID")
    }
}
