package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Path

/**
 * Value. What a refresh saw, for the next refresh to compare with:
 *
 * - [classPaths]: the Java class path of each module whose classes MPS manages;
 * - [archiveStamps]: the stamps of the archives that packaged modules are loaded from, `null` before the first refresh;
 * - [foldersWithStoredFilesLoaded]: the module folders whose stored VFS files were loaded into memory.
 */
internal data class RefreshBaseline(
    val classPaths: Map<SModuleReference, List<ClassPathRoot>>,
    val archiveStamps: Map<Path, ArchiveStamp?>? = null,
    val foldersWithStoredFilesLoaded: Set<Path> = emptySet(),
)

/** Value. A class path root and a fingerprint of its contents. */
internal data class ClassPathRoot(val path: Path, val fingerprint: String)

/** Value. The size and timestamp of an archive on disk. */
internal data class ArchiveStamp(val modified: Long, val size: Long)
