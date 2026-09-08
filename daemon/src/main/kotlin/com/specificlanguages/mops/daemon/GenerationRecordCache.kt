package com.specificlanguages.mops.daemon

import jetbrains.mps.generator.impl.dependencies.GenerationDependenciesCache
import jetbrains.mps.project.facets.GenerationTargetFacet
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.IFileSystem
import org.jetbrains.mps.openapi.model.SModel

/** An operation's generation records, read from disk without IDEA's cached file contents. */
internal class GenerationRecordCache(private val fileSystem: IFileSystem) : GenerationDependenciesCache() {
    private val locations = mutableMapOf<SModel, IFile?>()

    public override fun getCacheFile(model: SModel): IFile? = locations.getOrPut(model) {
        val candidates = GenerationTargetFacet.stream(model).use { facets ->
            facets.map { it.getOutputCacheLocation(model) }.toList().filterNotNull()
                .map { fileSystem.getFile(it.path).findChild(cacheFileName) }.filter { !it.isDirectory }
        }
        candidates.firstOrNull { it.exists() } ?: candidates.firstOrNull()
    }
}
