package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.ide.platform.watching.ReloadManager
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.Project
import jetbrains.mps.project.facets.JavaModuleFacet
import jetbrains.mps.vfs.refresh.DefaultCachingContext
import org.jetbrains.mps.openapi.module.SModuleReference
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Refreshes project files and replaces runtimes whose compiled output changed between requests. */
internal class ExternalProjectRefresh(private val project: Project) {
    private var outputs = snapshot()

    fun refresh() {
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

    // Replaced build outputs can retain both their timestamps and sizes.
    private fun snapshot(): Map<SModuleReference, Map<Path, String>> =
        project.modelAccess.computeReadAction {
            project.projectModulesWithGenerators.associate { module ->
                val root = module.getFacet(JavaModuleFacet::class.java)?.classesGen?.path?.let(Path::of)
                val files = if (root == null || !Files.isDirectory(root)) emptyMap() else {
                    Files.walk(root).use { paths ->
                        paths.filter(Files::isRegularFile).toList().associateWith {
                            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(it)))
                        }
                    }
                }
                module.moduleReference to files
            }
        }
}
