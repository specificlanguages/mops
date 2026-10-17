package com.specificlanguages.mops.daemon

import jetbrains.mps.classloading.ClassLoaderManager
import jetbrains.mps.module.ReloadableModule
import org.jetbrains.mps.openapi.module.SModule

object MopsRuntimeExtensions {
    /**
     * Loads a class through a module's class loader, which sees the module's own compiled classes and those of its
     * dependencies. Returns null when no such class exists there; module problems remain errors. Access: read.
     */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun loadClass(runtime: MopsRuntime, moduleTarget: String, className: String): Class<*>? {
        requireRead(runtime.mops, "mops.runtime.loadClass")
        val module = loadableModule(runtime.mops, moduleTarget)
        val classLoader = runtime.mops.project.getComponent(ClassLoaderManager::class.java).getClassLoader(module)
        return try {
            classLoader.loadClass(className)
        } catch (missing: ClassNotFoundException) {
            null
        }
    }

    /** Loads a class through a module's class loader, throwing when the class is missing. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun requireClass(runtime: MopsRuntime, moduleTarget: String, className: String): Class<*> {
        requireRead(runtime.mops, "mops.runtime.requireClass")
        return loadClass(runtime, moduleTarget, className)
            ?: throw IllegalArgumentException("class not found in module $moduleTarget: $className")
    }

    // ClassLoaderManager hands out a system-delegating class loader for a module it does not manage or has not
    // loaded, which would turn every lookup there into "class not found"; classify the module first so the failure
    // names what is actually wrong.
    private fun loadableModule(mops: Mops, target: String): SModule {
        val module = MopsLookupExtensions.requireModule(mops.lookup, target)
        require(module is ReloadableModule) {
            "module $target has no class loader: it is a ${module.javaClass.simpleName}, which carries no Java classes"
        }
        val diagnostic = ModuleLoadDiagnostics(mops.project).describe(module)
        val problem = diagnostic.problem ?: return module
        val name = module.moduleName ?: target
        throw IllegalStateException(
            buildString {
                if (loadFixableByMake(problem)) {
                    append("module $name is not compiled; run 'mops make module $name', then retry:\n")
                } else {
                    append("module $name has no loaded runtime, so its classes cannot be loaded:\n")
                }
                append(moduleLoadRootCauseLines(problem))
                append("\nrun 'mops diagnose module $name' for the full dependency tree")
            },
        )
    }
}
