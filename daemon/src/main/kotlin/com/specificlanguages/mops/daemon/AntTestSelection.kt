package com.specificlanguages.mops.daemon

import jetbrains.mps.project.AbstractModule
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Persistent identities and library paths needed by the independent Ant test environment. */
data class AntTestSelection(
    val modules: List<Path>,
    val moduleIds: List<String>,
    val libraries: List<Path>,
    val model: String?,
    val node: String?,
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
        Files.newOutputStream(path).use { properties.store(it, null) }
    }

    companion object {
        fun load(path: Path): AntTestSelection {
            val properties = Properties().also { properties -> Files.newInputStream(path).use { properties.load(it) } }
            fun list(name: String) = (0 until properties.getProperty("$name.count").toInt()).map { properties.getProperty("$name.$it") }
            return AntTestSelection(list("modules").map(Path::of), list("moduleIds"), list("libraries").map(Path::of),
                properties.getProperty("model"), properties.getProperty("node"))
        }

        fun resolve(access: JetBrainsMpsAccess, target: List<String>): AntTestSelection = access.read {
            val selection = access.resolveTestSelection(target)
            val modules = when (selection) {
                is SModule -> listOf(selection)
                is SModel -> listOf(requireNotNull(selection.module))
                is SNode -> {
                    require(selection.parent == null) { "Individual test methods are unsupported; select a test class, model, module, or project" }
                    listOf(requireNotNull(selection.model?.module))
                }
                else -> access.project.projectModulesWithGenerators.toList()
            }
            fun descriptors(modules: List<SModule>) = modules.mapNotNull { (it as? AbstractModule)?.descriptorFile?.path?.let(Path::of) }.distinct()
            AntTestSelection(descriptors(modules), modules.map { it.moduleId.toString() },
                descriptors(access.project.projectModulesWithGenerators.toList()),
                when (selection) {
                    is SModel -> selection.reference.toString()
                    is SNode -> requireNotNull(selection.model).reference.toString()
                    else -> null
                }, (selection as? SNode)?.reference?.toString())
        }
    }
}
