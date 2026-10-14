package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.protocol.ModuleLocationsJson
import jetbrains.mps.extapi.persistence.FileBasedModelRoot
import jetbrains.mps.library.ModulesMiner
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.facets.JavaModuleFacet
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

/**
 * Reports where MPS says a module's files are, reading only what the module itself records. See
 * https://github.com/specificlanguages/mps-api-research/blob/main/docs/module-file-locations.md for the API contracts.
 *
 * A deployed module's descriptor file is the `META-INF/module.xml` inside its jar, and the jar's path is the part of
 * that entry path before the `!/` separator. Its sources are not guessed from a `-src.jar` sibling: the deployment
 * descriptor names the sources jar and source descriptor, and MPS does not load a deployed module whose sources it
 * cannot resolve, so the resolved source descriptor is present for every loaded deployed module. A source module has
 * no deployment descriptor; its class output is the Java facet's `classes_gen`, which MPS forces to null for deployed
 * modules.
 *
 * Must run inside a read action: model roots are traversed.
 */
class ModuleLocationsExporter(
    private val persistence: PersistenceFacade = PersistenceFacade.getInstance(),
) {
    fun export(module: SModule): ModuleLocationsJson {
        val base = ModuleLocationsJson(
            kind = module.moduleKind(),
            name = module.moduleName ?: module.moduleReference.toString(),
            reference = persistence.asString(module.moduleReference),
            packaged = (module as? AbstractModule)?.isPackaged,
        )
        if (module !is AbstractModule) return base

        val descriptor = module.descriptorFile
        val deployment = module.moduleDescriptor?.deploymentDescriptor
        val sourceDescriptor = if (descriptor != null && deployment != null) {
            ModulesMiner.getSourceDescriptorFile(descriptor, deployment)
        } else {
            null
        }
        val facet = module.getFacet(JavaModuleFacet::class.java)
        return base.copy(
            descriptor = descriptor?.path,
            jar = descriptor?.path?.archivePath(),
            sourceDescriptor = sourceDescriptor?.path,
            sourcesJar = sourceDescriptor?.path?.archivePath(),
            sourceDirectory = if (deployment == null) module.moduleSourceDir?.path else null,
            classesGen = facet?.classesGen?.path,
            classPath = facet?.classPath?.toList() ?: emptyList(),
            modelRoots = module.modelRoots.filterIsInstance<FileBasedModelRoot>().mapNotNull { it.contentDirectory?.path },
        )
    }

    // A jar entry's path is `<jar path>!/<entry>`; a path without the separator is not inside an archive.
    private fun String.archivePath(): String? = if (contains(ARCHIVE_SEPARATOR)) substringBefore(ARCHIVE_SEPARATOR) else null

    private companion object {
        const val ARCHIVE_SEPARATOR = "!/"
    }
}
