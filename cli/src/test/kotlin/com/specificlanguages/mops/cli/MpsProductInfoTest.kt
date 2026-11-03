package com.specificlanguages.mops.cli

import com.specificlanguages.mops.daemoncomms.MpsProductInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

@Tag("smoke")
class MpsProductInfoTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `product descriptor defines classpath membership and order`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val zJar = emptyJar(mpsHome.resolve("lib/z.jar"))
        val aJar = emptyJar(mpsHome.resolve("lib/a.jar"))
        emptyJar(mpsHome.resolve("lib/undeclared.jar"))
        emptyJar(mpsHome.resolve("lib/modules/module.jar"))
        val mpsTool = emptyJar(mpsHome.resolve("lib/mpsant/mps-tool.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("Linux", "amd64", "z.jar", "missing.jar", "a.jar", "mpsant/mps-tool.jar"),
        )

        val classpath = MpsProductInfo.runtimeClasspath(mpsHome, "Linux", "x86_64")

        assertEquals(listOf(zJar.pathString, aJar.pathString, mpsTool.pathString), classpath)
        assertEquals(1, classpath.count { it == mpsTool.pathString })
        assertFalse(classpath.any { it.endsWith("undeclared.jar") || it.endsWith("module.jar") })
    }

    @Test
    fun `bootstrap classpath uses declared JVM argument and resolves IDE home`() {
        val mpsHome = tempDir.resolve("MPS app/Contents").createDirectories()
        mpsHome.resolve("Resources").createDirectories().resolve("product-info.json").writeText(
            """{"launch":[{"os":"macOS","arch":"aarch64","bootClassPathJarNames":[],
                "additionalJvmArguments":["-Xbootclasspath/a:${'$'}IDE_HOME/lib/nio-fs.jar","-Dunrelated=true"]}]}""",
        )

        assertEquals(
            listOf("-Xbootclasspath/a:$mpsHome/lib/nio-fs.jar"),
            MpsProductInfo.bootstrapJvmArgs(mpsHome, "Mac OS X", "arm64"),
        )
        assertEquals(emptyList(), MpsProductInfo.runtimeClasspath(mpsHome, "Mac OS X", "arm64"))
        assertEquals(emptyList(), MpsProductInfo.bootstrapJvmArgs(tempDir, "Linux", "amd64"))
    }

    @Test
    fun `bootstrap classpath resolves Windows IDE home placeholder in a path with spaces`() {
        val mpsHome = tempDir.resolve("Program Files/JetBrains/MPS 2026.2").createDirectories()
        mpsHome.resolve("product-info.json").writeText(
            """{"launch":[{"os":"Windows","arch":"amd64","bootClassPathJarNames":[],
                "additionalJvmArguments":["-Xbootclasspath/a:%IDE_HOME%/lib/nio-fs.jar"]}]}""",
        )

        assertEquals(
            listOf("-Xbootclasspath/a:$mpsHome/lib/nio-fs.jar"),
            MpsProductInfo.bootstrapJvmArgs(mpsHome, "Windows 11", "amd64"),
        )
    }

    @Test
    fun `exact host launch entry is preferred`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val linuxJar = emptyJar(mpsHome.resolve("lib/linux.jar"))
        emptyJar(mpsHome.resolve("lib/mac.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("macOS", "aarch64", "mac.jar"),
            launch("Linux", "amd64", "linux.jar"),
        )

        assertEquals(
            listOf(linuxJar.pathString),
            MpsProductInfo.runtimeClasspath(mpsHome, "Linux", "amd64"),
        )
    }

    @Test
    fun `sole nonmatching launch entry is accepted for generic distributions`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(mpsHome.resolve("product-info.json"), launch("Linux", "amd64", "boot.jar"))

        assertEquals(
            listOf(bootJar.pathString),
            MpsProductInfo.runtimeClasspath(mpsHome, "Mac OS X", "aarch64"),
        )
    }

    @Test
    fun `common boot classpath is accepted when no launch entry matches`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(
            mpsHome.resolve("product-info.json"),
            launch("Linux", "amd64", "boot.jar"),
            launch("Windows", "amd64", "boot.jar"),
        )

        assertEquals(
            listOf(bootJar.pathString),
            MpsProductInfo.runtimeClasspath(mpsHome, "Mac OS X", "arm64"),
        )
    }

    @Test
    fun `ambiguous nonmatching launch entries fail with descriptor path`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val descriptor = mpsHome.resolve("product-info.json")
        productInfo(
            descriptor,
            launch("Linux", "amd64", "linux.jar"),
            launch("Windows", "amd64", "windows.jar"),
        )

        val exception = assertFailsWith<IllegalStateException> {
            MpsProductInfo.runtimeClasspath(mpsHome, "Mac OS X", "aarch64")
        }

        assertContains(exception.message!!, "Ambiguous launch entries")
        assertContains(exception.message!!, descriptor.pathString)
    }

    @Test
    fun `product descriptor is found below Resources`() {
        val mpsHome = tempDir.resolve("MPS.app/Contents").createDirectories()
        val bootJar = emptyJar(mpsHome.resolve("lib/boot.jar"))
        productInfo(mpsHome.resolve("Resources/product-info.json"), launch("macOS", "aarch64", "boot.jar"))

        assertEquals(
            listOf(bootJar.pathString),
            MpsProductInfo.runtimeClasspath(mpsHome, "Mac OS X", "arm64"),
        )
    }

    @Test
    fun `missing product descriptor uses legacy classpath`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val aJar = emptyJar(mpsHome.resolve("lib/a.jar"))
        val zJar = emptyJar(mpsHome.resolve("lib/z.jar"))
        val moduleJar = emptyJar(mpsHome.resolve("lib/modules/module.jar"))
        val mpsTool = emptyJar(mpsHome.resolve("lib/mpsant/mps-tool.jar"))

        assertEquals(
            listOf(aJar.pathString, zJar.pathString, moduleJar.pathString, mpsTool.pathString),
            MpsProductInfo.runtimeClasspath(mpsHome, "Linux", "amd64"),
        )
    }

    @Test
    fun `malformed product descriptor does not use legacy classpath`() {
        val mpsHome = tempDir.resolve("mps").createDirectories()
        val descriptor = mpsHome.resolve("product-info.json")
        descriptor.writeText("""{"launch":[{"os":1,"arch":"amd64","bootClassPathJarNames":[]}]}""")
        emptyJar(mpsHome.resolve("lib/fallback.jar"))

        val exception = assertFailsWith<IllegalStateException> {
            MpsProductInfo.runtimeClasspath(mpsHome, "Linux", "amd64")
        }

        assertContains(exception.message!!, "Invalid MPS product descriptor")
        assertContains(exception.message!!, descriptor.pathString)
    }

    private fun emptyJar(path: Path): Path {
        path.parent.createDirectories()
        JarOutputStream(Files.newOutputStream(path)).use { }
        return path
    }

    private fun productInfo(path: Path, vararg launches: String) {
        path.parent.createDirectories()
        path.writeText("""{"launch":[${launches.joinToString()}]}""")
    }

    private fun launch(os: String, arch: String, vararg jars: String): String =
        """{"os":"$os","arch":"$arch","bootClassPathJarNames":[${jars.joinToString { "\"$it\"" }}]}"""
}
