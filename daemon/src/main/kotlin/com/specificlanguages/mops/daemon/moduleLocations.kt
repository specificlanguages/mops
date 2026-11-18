/**
 * Adapter. Hides: which archives, folders and class path roots on disk the modules in a project's repository are loaded
 * from.
 */
package com.specificlanguages.mops.daemon

import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.vfs.IFile
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Path

/** The archives that the packaged modules in [project]'s repository are loaded from. Requires model read access. */
internal fun moduleArchives(project: Project): Set<Path> =
    project.repository.modules.filterIsInstance<AbstractModule>()
        .flatMap { module -> listOfNotNull(module.descriptorFile) + sourceLocations(module) }
        .filter { it.isInZipArchive }.map { Path.of(it.path.substringBefore("!/")) }.toSet()


/**
 * The local folders that the modules in [project]'s repository are loaded from, outside the MPS distribution at
 * [mpsHome] and none inside another: the folder of the module descriptor, the module source folder, and the content
 * folders of file-based model roots. This covers project modules and the modules of libraries that point at source
 * folders. Requires model read access.
 */
internal fun moduleFolders(project: Project, mpsHome: Path): List<Path> {
    val distribution = setOf(mpsHome.normalize(), mpsHome.toRealPath())
    val folders = project.repository.modules.filterIsInstance<AbstractModule>()
        .flatMap { module -> listOfNotNull(module.descriptorFile?.parent) + sourceLocations(module) }
        .filterNot { it.isInZipArchive }.map { Path.of(it.path).normalize() }
        .filterNot { folder -> distribution.any(folder::startsWith) }.distinct()
    return folders.filter { folder -> folders.none { it != folder && folder.startsWith(it) } }
}


/**
 * The Java class path roots of each module that [project]'s modules need at run time and whose classes MPS manages. A
 * nested archive entry is reported as its containing archive, which is replaced when the entry changes. Requires model
 * read access.
 */
internal fun managedClassPaths(project: Project): Map<SModuleReference, List<Path>> =
    GlobalModuleDependenciesManager(project.projectModulesWithGenerators)
        .getModules(GlobalModuleDependenciesManager.Deptype.EXECUTE)
        .mapNotNull { module ->
            val facet = module.getFacet(JavaModuleFacet::class.java)
            if (facet == null || facet.loadClasses != JavaModuleFacet.LoadClasses.ManagedByMPS) return@mapNotNull null
            module.moduleReference to facet.classPath.map { entry -> Path.of(entry.substringBefore("!/")) }
        }.toMap()


/** The module source folder of [module] and the content folders of its file-based model roots. */
private fun sourceLocations(module: AbstractModule): List<IFile> =
    listOfNotNull(module.moduleSourceDir) +
        module.modelRoots.filterIsInstance<FileBasedModelRoot>().mapNotNull { it.contentDirectory }

