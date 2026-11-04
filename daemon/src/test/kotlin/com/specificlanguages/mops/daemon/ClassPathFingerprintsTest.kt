package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import java.util.Collections
import kotlin.io.path.*
import kotlin.test.*

class ClassPathFingerprintsTest {
    @TempDir
    lateinit var tempDir: Path

    private val hashed = Collections.synchronizedList(mutableListOf<Path>())

    /** A clock far enough ahead that no file counts as recently changed. */
    private fun calculator(now: () -> Instant = { Instant.now().plus(Duration.ofHours(1)) }) =
        ClassPathFingerprints(now) { file -> hashed.add(file); file.readBytes() }

    @Test
    fun `unchanged roots are not hashed again`() {
        val classes = tempDir.resolve("classes").createDirectories()
        classes.resolve("a/A.class").createParentDirectories().writeText("A")
        val jar = tempDir.resolve("lib.jar").also { it.writeText("jar") }
        val fingerprints = calculator()

        val first = fingerprints.compute(listOf(classes, jar))
        hashed.clear()
        val second = fingerprints.compute(listOf(classes, jar))

        assertEquals(first, second)
        assertEquals(emptyList(), hashed)
    }

    @Test
    fun `file rewritten with the same size and timestamp changes the fingerprint`() {
        val classes = tempDir.resolve("classes").createDirectories()
        val file = classes.resolve("A.class").also { it.writeText("before") }
        val jar = tempDir.resolve("lib.jar").also { it.writeText("jar-before") }
        val fingerprints = calculator()
        val first = fingerprints.compute(listOf(classes, jar))

        rewriteKeepingStamp(file, "after!")
        rewriteKeepingStamp(jar, "jar-after!")
        val second = fingerprints.compute(listOf(classes, jar))

        assertNotEquals(first[classes], second[classes])
        assertNotEquals(first[jar], second[jar])
    }

    @Test
    fun `file replaced by another one with the same size and timestamp changes the fingerprint`() {
        val jar = tempDir.resolve("lib.jar").also { it.writeText("before") }
        val fingerprints = calculator()
        val first = fingerprints.compute(listOf(jar))

        val modified = jar.getLastModifiedTime()
        val replacement = tempDir.resolve("replacement.jar").also { it.writeText("after!") }
        replacement.setLastModifiedTime(modified)
        Files.move(replacement, jar, StandardCopyOption.REPLACE_EXISTING)
        val second = fingerprints.compute(listOf(jar))

        assertEquals(modified, jar.getLastModifiedTime())
        assertNotEquals(first[jar], second[jar])
    }

    @Test
    fun `added, removed and renamed files change the fingerprint`() {
        val classes = tempDir.resolve("classes").createDirectories()
        val file = classes.resolve("A.class").also { it.writeText("A") }
        val fingerprints = calculator()
        val original = fingerprints.compute(listOf(classes)).getValue(classes)

        val added = classes.resolve("B.class").also { it.writeText("B") }
        assertNotEquals(original, fingerprints.compute(listOf(classes)).getValue(classes))
        added.deleteExisting()
        assertEquals(original, fingerprints.compute(listOf(classes)).getValue(classes))
        file.moveTo(classes.resolve("C.class"))
        assertNotEquals(original, fingerprints.compute(listOf(classes)).getValue(classes))
    }

    @Test
    fun `missing root has a fingerprint that changes when the root appears`() {
        val jar = tempDir.resolve("lib.jar")
        val fingerprints = calculator()
        val missing = fingerprints.compute(listOf(jar)).getValue(jar)

        jar.writeText("jar")

        assertNotEquals(missing, fingerprints.compute(listOf(jar)).getValue(jar))
    }

    @Test
    fun `recently changed file is hashed again on the next call`() {
        val jar = tempDir.resolve("lib.jar").also { it.writeText("jar") }
        val fingerprints = calculator(now = Instant::now)
        fingerprints.compute(listOf(jar))
        hashed.clear()

        fingerprints.compute(listOf(jar))

        assertEquals(listOf(jar), hashed)
    }

    private fun rewriteKeepingStamp(file: Path, content: String) {
        val modified = file.getLastModifiedTime()
        val size = file.fileSize()
        file.writeText(content)
        file.setLastModifiedTime(modified)
        assertEquals(size, file.fileSize())
    }
}
