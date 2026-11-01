package com.specificlanguages.mops.daemon

import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Persistent identities and library paths needed by the independent Ant test environment, plus the [description] of
 * the Test Selection they were resolved from, for diagnostics about the selection.
 */
data class AntTestSelection(
    val modules: List<Path>,
    val moduleIds: List<String>,
    val libraries: List<Path>,
    val model: String?,
    val node: String?,
    val description: String,
) {
    fun save(path: Path) {
        val properties = Properties()
        fun list(name: String, values: List<String>) {
            properties.setProperty("$name.count", values.size.toString())
            values.forEachIndexed { index, value -> properties.setProperty("$name.$index", value) }
        }
        list("modules", modules.map(Path::toString))
        list("moduleIds", moduleIds)
        list("libraries", libraries.map(Path::toString))
        model?.let { properties.setProperty("model", it) }
        node?.let { properties.setProperty("node", it) }
        properties.setProperty("description", description)
        Files.newOutputStream(path).use { properties.store(it, null) }
    }

    companion object {
        fun load(path: Path): AntTestSelection {
            val properties = Properties().also { properties -> Files.newInputStream(path).use { properties.load(it) } }
            fun list(name: String) = (0 until properties.getProperty("$name.count").toInt()).map { properties.getProperty("$name.$it") }
            return AntTestSelection(list("modules").map(Path::of), list("moduleIds"), list("libraries").map(Path::of),
                properties.getProperty("model"), properties.getProperty("node"), properties.getProperty("description"))
        }

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
            AntTestSelection(descriptors(modules), modules.map { it.moduleId.toString() },
                descriptors(project.projectModulesWithGenerators.toList()),
                when (selection) {
                    is SModel -> selection.reference.toString()
                    is SNode -> requireNotNull(selection.model).reference.toString()
                    else -> null
                }, (selection as? SNode)?.reference?.toString(), describe(selection))
        }
    }
}
