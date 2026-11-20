/**
 * Logic. Hides: which changes since the previous refresh make a refresh refresh a module archive, load the stored files
 * of a module folder, or replace a module's runtime.
 */
package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Path

/**
 * The archives to refresh. The first refresh ([previous] is `null`) refreshes every archive, because the persistent VFS
 * may predate this daemon. Later refreshes refresh the archives whose stamp changed. [current] maps a missing archive to
 * `null`, so an archive that appears or disappears on disk counts as changed.
 */
internal fun archivesToRefresh(previous: Map<Path, ArchiveStamp?>?, current: Map<Path, ArchiveStamp?>): Set<Path> =
    if (previous == null) current.keys else current.filter { (archive, stamp) -> previous[archive] != stamp }.keys

/** The module folders in [folders] whose stored files are not in [loaded] yet, in order and without duplicates. */
internal fun foldersToLoad(loaded: Set<Path>, folders: List<Path>): List<Path> =
    folders.distinct().filterNot { it in loaded }

/** The modules whose runtimes to replace: those whose class path or class path contents changed. */
internal fun modulesToReload(
    previous: Map<SModuleReference, List<ClassPathRoot>>,
    current: Map<SModuleReference, List<ClassPathRoot>>,
): Set<SModuleReference> = current.filter { (module, roots) -> previous[module] != roots }.keys
