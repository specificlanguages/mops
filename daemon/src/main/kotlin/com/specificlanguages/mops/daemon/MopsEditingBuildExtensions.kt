package com.specificlanguages.mops.daemon

import jetbrains.mps.messages.IMessage
import jetbrains.mps.messages.IMessageHandler
import jetbrains.mps.messages.MessageKind
import jetbrains.mps.tool.run.ModuleClassCode
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.model.SNodeReference
import org.jetbrains.mps.openapi.model.EditableSModel
import java.lang.reflect.InvocationTargetException

object MopsEditingBuildExtensions {
    private const val BUILD_PROJECT_CONCEPT = "jetbrains.mps.build.structure.BuildProject"
    private const val BUILD_LANGUAGE_MODULE =
        "0cf935df-4699-4e9c-a132-fa109541cba3(jetbrains.mps.build.mps)"
    private const val MODULE_LOADER = "jetbrains.mps.build.mps.util.ModuleLoader"
    private const val CHECK_TYPE = "jetbrains.mps.build.mps.util.ModuleChecker\$CheckType"

    /** Reloads the nearest containing build project's existing module entries from their descriptors. */
    @CodeModeExtension(MpsAccessLevel.COMMAND)
    @JvmStatic
    fun reloadModulesFromDisk(build: MopsEditingBuild, node: SNode): BuildModuleReloadResult {
        requireOwner(build.mops)
        requireCommand("mops.editing.build.reloadModulesFromDisk")

        val buildProject = generateSequence(node) { it.parent }
            .firstOrNull { it.concept.qualifiedName == BUILD_PROJECT_CONCEPT }
            ?: throw IllegalArgumentException("node has no BuildProject ancestor")
        requireEditableProjectNode(build, buildProject)

        val messages = mutableListOf<BuildModuleReloadMessage>()
        val handler = IMessageHandler { message -> collect(message, build, messages) }
        val code = ModuleClassCode(BUILD_LANGUAGE_MODULE)
        code.load(build.mops.platform, MODULE_LOADER)

        val constructor = code.cons(SNode::class.java, IMessageHandler::class.java)
            .orElseThrow { IllegalStateException("$MODULE_LOADER(SNode, IMessageHandler) is unavailable") }
        val loader = invokeReflectively { constructor.newInstance(buildProject, handler) }
        val checkTypeClass = loader.javaClass.classLoader.loadClass(CHECK_TYPE)
        val loadImportantPart = checkTypeClass.enumConstants.single { (it as Enum<*>).name == "LOAD_IMPORTANT_PART" }
        val checkAllModules = code.instanceMethod("checkAllModules", checkTypeClass)
            .orElseThrow { IllegalStateException("$MODULE_LOADER.checkAllModules(CheckType) is unavailable") }
        invokeReflectively { checkAllModules.invoke(loader, loadImportantPart) }

        return BuildModuleReloadResult(
            succeeded = messages.none { it.kind == "error" },
            messages = messages,
        )
    }

    private fun requireEditableProjectNode(build: MopsEditingBuild, buildProject: SNode) {
        val model = buildProject.model
            ?: throw IllegalArgumentException("BuildProject is detached")
        require(model.repository != null) { "BuildProject is not attached to a repository" }
        val module = model.module
            ?: throw IllegalArgumentException("BuildProject model is not owned by a module")
        require(build.mops.project.isProjectModule(module)) { "BuildProject is not in a Project Module of the current project" }
        require(model is EditableSModel && !model.isReadOnly) { "BuildProject model is not editable" }
    }

    private fun collect(
        message: IMessage,
        build: MopsEditingBuild,
        messages: MutableList<BuildModuleReloadMessage>,
    ) {
        val kind = when (message.kind) {
            MessageKind.WARNING -> "warning"
            MessageKind.ERROR -> "error"
            else -> return
        }
        val node = when (val hint = message.hintObject) {
            is SNode -> hint
            is SNodeReference -> hint.resolve(build.mops.project.repository)
            else -> null
        }
        messages += BuildModuleReloadMessage(kind, message.text.orEmpty(), node)
    }

    private fun <T> invokeReflectively(action: () -> T): T =
        try {
            action()
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
}
