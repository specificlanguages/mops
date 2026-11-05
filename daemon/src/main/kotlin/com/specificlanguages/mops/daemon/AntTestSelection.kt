package com.specificlanguages.mops.daemon

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import jetbrains.mps.project.PathMacros
import jetbrains.mps.project.ProjectLibraryManager
import jetbrains.mps.util.MacrosFactory
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Persistent identities, repository contents, and path macros needed by the independent Ant test environment, plus the
 * [description] of the Test Selection they were resolved from, for diagnostics about the selection.
 *
 * The worker's repository holds the project's module descriptors ([repositoryModules]) and the folders of the
 * project's libraries ([repositoryFolders]) as the daemon loaded them. [macros] are the path macros the daemon defines.
 * [diagnostics] explain what the daemon could not pass on, such as a project library whose path uses an undefined
 * path macro.
 */
data class AntTestSelection(
    val testModules: List<Path>,
    val testModuleIds: List<String>,
    val repositoryModules: List<Path>,
    val repositoryFolders: List<Path>,
    val macros: Map<String, String>,
    val model: String?,
    val node: String?,
    val description: String,
    val diagnostics: List<String>,
) {
    /** The folders a run of this selection on [project] reads modules and models from, none inside another. */
    fun sourceFolders(project: Path): List<Path> {
        val moduleFolders = (testModules + repositoryModules).map { it.parent }
        val folders = (listOf(project) + repositoryFolders + moduleFolders).map { it.normalize() }.distinct()
        return folders.filter { folder -> folders.none { it != folder && folder.startsWith(it) } }
    }

    fun save(path: Path) {
        val properties = Properties()
        fun list(name: String, values: List<String>) {
            properties.setProperty("$name.count", values.size.toString())
            values.forEachIndexed { index, value -> properties.setProperty("$name.$index", value) }
        }
        list("testModules", testModules.map(Path::toString))
        list("testModuleIds", testModuleIds)
        list("repositoryModules", repositoryModules.map(Path::toString))
        list("repositoryFolders", repositoryFolders.map(Path::toString))
        macros.forEach { (name, value) -> properties.setProperty("$MACRO_PREFIX$name", value) }
        model?.let { properties.setProperty("model", it) }
        node?.let { properties.setProperty("node", it) }
        properties.setProperty("description", description)
        list("diagnostics", diagnostics)
        Files.newOutputStream(path).use { properties.store(it, null) }
    }

    companion object {
        fun load(path: Path): AntTestSelection {
            val properties = Properties().also { properties -> Files.newInputStream(path).use { properties.load(it) } }
            fun list(name: String) = (0 until properties.getProperty("$name.count").toInt()).map { properties.getProperty("$name.$it") }
            return AntTestSelection(
                testModules = list("testModules").map(Path::of),
                testModuleIds = list("testModuleIds"),
                repositoryModules = list("repositoryModules").map(Path::of),
                repositoryFolders = list("repositoryFolders").map(Path::of),
                macros = properties.stringPropertyNames().filter { it.startsWith(MACRO_PREFIX) }.sorted()
                    .associate { it.removePrefix(MACRO_PREFIX) to properties.getProperty(it) },
                model = properties.getProperty("model"),
                node = properties.getProperty("node"),
                description = properties.getProperty("description"),
                diagnostics = list("diagnostics"),
            )
        }

        private const val MACRO_PREFIX = "macro."

        /** Names the resolved Test Selection by its identity: the project, a module, a model, or a root test case. */
        fun describe(selection: Any): String = when (selection) {
            is SModule -> "module ${selection.moduleName ?: selection.moduleReference}"
            is SModel -> "model ${selection.name.value}"
            is SNode -> "root test case ${selection.name ?: selection.reference} in model ${requireNotNull(selection.model).name.value}"
            else -> "project (no target given)"
        }

        /**
         * Resolves a **Test Selection** given as a **Navigation Target**: the project when [target] is empty, otherwise the
         * module, model, or node the path names. Must be called inside a model read action.
         */
        fun select(project: MPSProject, target: List<String>): Any {
            if (target.isEmpty()) return project
            return when (val resolution = NavigationTargetResolver(project).resolve(target)) {
                is NavigationTargetResolution.Found -> when (val selected = resolution.target) {
                    is NavigationTarget.Module -> selected.module
                    is NavigationTarget.Model -> selected.model
                    is NavigationTarget.RootNode -> selected.node
                    is NavigationTarget.Node -> selected.node
                }
                is NavigationTargetResolution.Ambiguous -> throw IllegalArgumentException(resolution.message)
                is NavigationTargetResolution.Missing -> throw IllegalArgumentException(
                    "test selection not found: ${target.joinToString(" ")}" + resolution.detailLine(),
                )
            }
        }

        fun resolve(project: MPSProject, target: List<String>): AntTestSelection = project.modelAccess.computeReadAction {
            val selection = select(project, target)
            val modules = when (selection) {
                is SModule -> listOf(selection)
                is SModel -> listOf(requireNotNull(selection.module))
                is SNode -> {
                    require(selection.parent == null) { "Individual test methods are unsupported; select a test class, model, module, or project" }
                    listOf(requireNotNull(selection.model?.module))
                }
                else -> project.projectModulesWithGenerators.toList()
            }
            fun descriptors(modules: List<SModule>) = modules.mapNotNull { (it as? AbstractModule)?.descriptorFile?.path?.let(Path::of) }.distinct()
            // Paths come back with macros expanded the way the daemon loaded the libraries; an undefined macro stays as
            // written. MPS skips a library without a path, and so does the worker's repository.
            val (unexpanded, libraries) = ProjectLibraryManager.getInstance(project.project).uiLibraries
                .mapNotNull { library -> library.path?.let { library.name to it } }.sortedBy { it.first }
                .partition { (_, path) -> MacrosFactory.containsMacro(path) }
            AntTestSelection(
                testModules = descriptors(modules),
                testModuleIds = modules.map { it.moduleId.toString() },
                repositoryModules = descriptors(project.projectModulesWithGenerators.toList()),
                repositoryFolders = libraries.map { (_, path) -> Path.of(path) },
                macros = pathMacros(project),
                model = when (selection) {
                    is SModel -> selection.reference.toString()
                    is SNode -> requireNotNull(selection.model).reference.toString()
                    else -> null
                },
                node = (selection as? SNode)?.reference?.toString(),
                description = describe(selection),
                diagnostics = unexpanded.map { (name, path) ->
                    "Project library $name is not in the test worker's repository: its path $path uses a path macro the daemon does not define"
                },
            )
        }

        /** The user-defined path macros the daemon knows, from its environment configuration and IDEA path variables. */
        private fun pathMacros(project: MPSProject): Map<String, String> {
            val macros = requireNotNull(project.platform.findComponent(PathMacros::class.java))
            return macros.userNames.sorted().mapNotNull { name -> macros.getValue(name)?.let { name to it } }.toMap()
        }
    }
}
