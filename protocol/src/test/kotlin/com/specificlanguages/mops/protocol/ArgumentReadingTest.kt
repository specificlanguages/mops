package com.specificlanguages.mops.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ArgumentReadingTest {
    @Test
    fun `names one argument and its role`() {
        assertEquals("mops read `a b` as one target path", argumentReading("a b" to "one target path"))
    }

    @Test
    fun `joins several arguments with and`() {
        assertEquals(
            "mops read `m` as MODEL_TARGET and `1` as NODE_ID",
            argumentReading("m" to "MODEL_TARGET", "1" to "NODE_ID"),
        )
    }
}
