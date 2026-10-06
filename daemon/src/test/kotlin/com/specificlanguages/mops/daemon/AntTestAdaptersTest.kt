package com.specificlanguages.mops.daemon

import java.io.DataInputStream
import java.util.jar.JarInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AntTestAdaptersTest {
    @Test
    fun `packaged adapters preserve classloader boundaries and Java 17 compatibility`() {
        val classes = listOf("boot", "legacy", "current").associateWith { variant ->
            val resource = requireNotNull(javaClass.getResourceAsStream("/testing/ant/$variant.jar"))
            JarInputStream(resource).use { jar ->
                buildSet {
                    while (true) {
                        val entry = jar.nextJarEntry ?: break
                        if (!entry.name.endsWith(".class")) continue
                        val header = DataInputStream(jar)
                        assertEquals(0xCAFEBABE.toInt(), header.readInt(), entry.name)
                        header.readUnsignedShort() // Minor version.
                        assertEquals(61, header.readUnsignedShort(), "$variant: ${entry.name}")
                        add(entry.name.substringAfterLast('/').removeSuffix(".class"))
                    }
                }
            }
        }
        assertEquals(setOf("AntTestWorker", "MopsLaunchTestTask", "LauncherApi"), classes.getValue("boot"))
        for (variant in listOf("legacy", "current")) {
            val names = classes.getValue(variant)
            assertTrue(names.containsAll(setOf("ModelLauncher", "SelectionSupport")), variant)
            assertTrue(names.all { it == "ModelLauncher" || it.startsWith("SelectionSupport") }, "$variant: $names")
        }
    }
}
