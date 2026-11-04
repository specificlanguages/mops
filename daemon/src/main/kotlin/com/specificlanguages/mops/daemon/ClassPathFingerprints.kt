package com.specificlanguages.mops.daemon

import java.nio.file.FileSystems
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Computes fingerprints of class path roots (class folders and archives) from the names and contents of their files.
 *
 * A replaced class file or archive can keep both its size and its timestamp, so these alone cannot tell whether a file
 * changed. Each content hash is kept together with the file's stamp: size, modification time, file key (device and
 * inode) and status change time (`ctime`). Writing a file or setting its modification time updates its status change
 * time, and replacing a file by another one gives it a new file key, so a file whose stamp is unchanged keeps its
 * cached hash. On file systems without status change times (Windows), every file is hashed on every call.
 *
 * A file whose status change time is within [racyWindow] of the start of a call is hashed again on the next call,
 * because a second change inside the same timestamp tick would leave its stamp unchanged.
 */
internal class ClassPathFingerprints(
    private val now: () -> Instant = Instant::now,
    private val hashFile: (Path) -> ByteArray = ::sha256,
) {
    private var hashes = mapOf<Path, CachedHash>()

    /**
     * Returns the fingerprint of each root. Roots are walked in parallel because the walk, one `stat` call per file, is
     * most of the cost when nothing changed. Only the files of these roots stay cached for the next call.
     */
    fun fingerprints(roots: Collection<Path>): Map<Path, String> {
        val racyAfter = now().minus(racyWindow)
        val current = ConcurrentHashMap<Path, CachedHash>()
        val result = roots.distinct().parallelStream()
            .map { root -> root to fingerprint(root, racyAfter, current) }
            .toList().toMap()
        hashes = current
        return result
    }

    private fun fingerprint(root: Path, racyAfter: Instant, current: MutableMap<Path, CachedHash>): String {
        if (!Files.exists(root)) return "missing"
        val files = mutableListOf<Pair<Path, Map<String, Any?>?>>()
        Files.walkFileTree(root, setOf(FOLLOW_LINKS), Int.MAX_VALUE, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (attributes.isRegularFile) files.add(file to stamp(file))
                return FileVisitResult.CONTINUE
            }
        })
        files.sortBy { it.first }
        val digest = MessageDigest.getInstance("SHA-256")
        for ((file, stamp) in files) {
            val hash = current.getOrPut(file) { cachedHash(file, stamp, racyAfter) }
            digest.update(root.relativize(file).toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(hash.hash)
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun cachedHash(file: Path, stamp: Map<String, Any?>?, racyAfter: Instant): CachedHash {
        val previous = hashes[file]
        if (stamp != null && previous != null && previous.stamp == stamp) return previous
        val trusted = stamp?.takeUnless { (it[CHANGE_TIME] as FileTime).toInstant() > racyAfter }
        return CachedHash(trusted, hashFile(file))
    }

    /** The attributes whose values are equal while a file is unchanged, or `null` without status change times. */
    private fun stamp(file: Path): Map<String, Any?>? =
        if (unixAttributes) Files.readAttributes(file, STAMP_ATTRIBUTES) else null

    private class CachedHash(val stamp: Map<String, Any?>?, val hash: ByteArray)

    companion object {
        private const val CHANGE_TIME = "ctime"
        private const val STAMP_ATTRIBUTES = "unix:size,lastModifiedTime,fileKey,$CHANGE_TIME"
        private val racyWindow = Duration.ofSeconds(2)
        private val unixAttributes = "unix" in FileSystems.getDefault().supportedFileAttributeViews()

        private fun sha256(file: Path): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            Files.newInputStream(file).use { input ->
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
            return digest.digest()
        }
    }
}
