package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.configurationStore.StoreReloadManager
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.Project
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.project.dependency.GlobalModuleDependenciesManager
import jetbrains.mps.vfs.refresh.DefaultCachingContext
import kotlinx.coroutines.runBlocking
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.FileVisitOption.FOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Refreshes project files and replaces runtimes whose Java classpath contents changed between requests. */
internal class ExternalProjectRefresh(private val project: Project) {
    private var outputs = snapshot()

    fun refresh() {
        val mpsProject = project as MPSProject
        ApplicationManager.getApplication().invokeAndWait {
            val settings = Path.of(requireNotNull(mpsProject.project.basePath)).resolve(".mps")
            mpsProject.fileSystem.getFile(settings.toString()).refresh(DefaultCachingContext(true, true))
        }
        // The component store reloads project membership separately from MPS's model/module reload sessions.
        runBlocking { StoreReloadManager.getInstance(mpsProject.project).reloadChangedStorageFiles() }
        ApplicationManager.getApplication().invokeAndWait {
            val roots = project.modelAccess.computeReadAction {
                project.projectModules.mapNotNull { (it as? AbstractModule)?.descriptorFile?.parent }.distinct()
            }
            roots.forEach { it.refresh(DefaultCachingContext(true, true)) }
            ReloadManager.getInstance().flush()
            val current = snapshot()
            val changed = current.filter { (module, files) -> outputs[module] != files }.keys
            if (changed.isNotEmpty()) {
                project.modelAccess.runWriteAction {
                    val modules = changed.mapNotNull { it.resolve(project.repository) }
                    project.getComponent(ClassLoaderManager::class.java).reloadModules(modules)
                }
            }
            outputs = current
        }
    }

    // Replaced classes and libraries can retain both their timestamps and sizes.
    private fun snapshot(): Map<SModuleReference, List<Pair<Path, String>>> =
        project.modelAccess.computeReadAction {
            val fingerprints = mutableMapOf<Path, String>()
            val modules = GlobalModuleDependenciesManager(project.projectModulesWithGenerators)
                .getModules(GlobalModuleDependenciesManager.Deptype.EXECUTE)
            modules.mapNotNull { module ->
                val facet = module.getFacet(JavaModuleFacet::class.java)
                if (facet == null || facet.loadClasses != JavaModuleFacet.LoadClasses.ManagedByMPS) return@mapNotNull null
                val files = facet.classPath.map { entry ->
                    // A nested archive entry is replaced when its containing archive changes.
                    val path = Path.of(entry.substringBefore("!/"))
                    path to fingerprints.getOrPut(path) { fingerprint(path) }
                }
                module.moduleReference to files
            }.toMap()
        }

    private fun fingerprint(root: Path): String {
        if (!Files.exists(root)) return "missing"
        val digest = MessageDigest.getInstance("SHA-256")
        val files = if (Files.isDirectory(root)) {
            Files.walk(root, FOLLOW_LINKS).use { paths -> paths.filter(Files::isRegularFile).sorted().toList() }
        } else listOf(root)
        val buffer = ByteArray(8192)
        for (file in files) {
            digest.update(root.relativize(file).toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            digest.update(Files.size(file).toString().toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            Files.newInputStream(file).use { input ->
                var count = input.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = input.read(buffer)
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
