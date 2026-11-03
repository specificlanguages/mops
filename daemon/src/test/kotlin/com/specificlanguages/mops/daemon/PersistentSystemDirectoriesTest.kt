package com.specificlanguages.mops.daemon

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersistentSystemDirectoriesTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `a free role gets the persistent directory of its MPS build again after release`() {
        val cache = directory.resolve("cache")
        val directories = PersistentSystemDirectories(cache, "MPS-241.19416.1452")
        directories.acquire("worker", "test worker", directory.resolve("run-1")).use { worker ->
            assertEquals(cache.resolve("MPS-241.19416.1452/worker"), worker.path)
            assertNull(worker.diagnostic)
        }
        directories.acquire("worker", "test worker", directory.resolve("run-2")).use { worker ->
            assertEquals(cache.resolve("MPS-241.19416.1452/worker"), worker.path)
        }
        PersistentSystemDirectories(cache, "MPS-251.28293.10283").acquire("worker", "test worker", directory.resolve("run-3")).use {
            assertEquals(cache.resolve("MPS-251.28293.10283/worker"), it.path)
        }
    }

    @Test
    fun `a role locked by another process falls back to the run's own directory and says so`() {
        val cache = directory.resolve("cache")
        val directories = PersistentSystemDirectories(cache, "MPS-241.19416.1452")
        val holder = holdLock(cache.resolve("MPS-241.19416.1452/worker.lock"))
        try {
            directories.acquire("worker", "test worker", directory.resolve("run/test-system")).use { worker ->
                assertEquals(directory.resolve("run/test-system"), worker.path)
                assertEquals("The test worker's system directory ${cache.resolve("MPS-241.19416.1452/worker")} is in use by " +
                    "another test run; this run uses the fresh directory ${directory.resolve("run/test-system")} and indexes " +
                    "the MPS distribution again.", worker.diagnostic)
            }
            directories.acquire("preparation", "preparation environment", directory.resolve("run/idea-system")).use {
                assertEquals(cache.resolve("MPS-241.19416.1452/preparation"), it.path)
            }
        } finally {
            holder.destroyForcibly().waitFor()
        }
        directories.acquire("worker", "test worker", directory.resolve("run/test-system")).use {
            assertEquals(cache.resolve("MPS-241.19416.1452/worker"), it.path)
        }
    }

    @Test
    fun `a role held in this process is busy for a second run that leaves the first run's lock in place`() {
        val directories = PersistentSystemDirectories(directory, "MPS-241.19416.1452")
        directories.acquire("worker", "test worker", directory.resolve("run-1")).use {
            directories.acquire("worker", "test worker", directory.resolve("run-2")).use { second ->
                assertEquals(directory.resolve("run-2"), second.path)
            }
            assertEquals("busy", probeLock(directory.resolve("MPS-241.19416.1452/worker.lock")))
        }
        assertEquals("free", probeLock(directory.resolve("MPS-241.19416.1452/worker.lock")))
    }

    /** Asks another JVM whether it can lock [file] now. */
    private fun probeLock(file: Path): String {
        val source = directory.resolve("LockProbe.java")
        source.writeText("""
            import java.nio.channels.FileChannel;
            import java.nio.file.*;
            public class LockProbe {
                public static void main(String[] args) throws Exception {
                    var channel = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                    System.out.println(channel.tryLock() == null ? "busy" : "free");
                }
            }
        """.trimIndent())
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(java, source.toString(), file.toString()).redirectErrorStream(true).start()
        return process.inputReader().readText().trim().also { process.waitFor() }
    }

    /** Starts a JVM that holds an exclusive lock on [file] until it is killed, and waits until it holds it. */
    private fun holdLock(file: Path): Process {
        file.parent.createDirectories()
        val source = directory.resolve("LockHolder.java")
        source.writeText("""
            import java.nio.channels.FileChannel;
            import java.nio.file.*;
            public class LockHolder {
                public static void main(String[] args) throws Exception {
                    var lock = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.WRITE).lock();
                    System.out.println("locked");
                    Thread.sleep(Long.MAX_VALUE);
                }
            }
        """.trimIndent())
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(java, source.toString(), file.toString()).redirectErrorStream(true).start()
        assertEquals("locked", process.inputReader().readLine())
        return process
    }
}
