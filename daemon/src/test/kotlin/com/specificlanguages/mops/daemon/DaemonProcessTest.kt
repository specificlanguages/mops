package com.specificlanguages.mops.daemon

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DaemonProcessTest {

    @Test
    fun `a native library that does not load leaves the daemon attached with a warning`() {
        val warnings = ByteArrayOutputStream()

        DaemonProcess.detach(PrintStream(warnings, true), "JNA (jna.boot.library.path=C:\\mps\\lib\\jna)") {
            throw UnsatisfiedLinkError("Native library (com/sun/jna/win32-x86-64/jnidispatch.dll) not found in resource path")
        }

        val warning = warnings.toString()
        assertContains(warning, "did not detach")
        assertContains(warning, "JNA (jna.boot.library.path=C:\\mps\\lib\\jna)")
        assertContains(warning, "jnidispatch.dll) not found in resource path")
    }

    @Test
    fun `native access that is not enabled leaves the daemon attached with a warning`() {
        val warnings = ByteArrayOutputStream()

        DaemonProcess.detach(PrintStream(warnings, true), "the Foreign Function API") {
            throw IllegalCallerException("Illegal native access")
        }

        assertContains(warnings.toString(), "did not detach")
    }

    @Test
    fun `a failing detach call still fails the daemon`() {
        val warnings = ByteArrayOutputStream()

        assertFailsWith<IllegalStateException> {
            DaemonProcess.detach(PrintStream(warnings, true), "JNA (jna.boot.library.path=/mps/lib/jna)") {
                error("Could not detach daemon session: errno 1")
            }
        }
        assertEquals("", warnings.toString())
    }
}
