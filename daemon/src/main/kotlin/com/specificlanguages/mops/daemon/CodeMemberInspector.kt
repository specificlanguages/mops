package com.specificlanguages.mops.daemon

import java.beans.Introspector
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** Reads public member signatures without invoking methods, getters, or class initializers. */
object CodeMemberInspector {
    private val knownTypes = listOf(
        "jetbrains.mps.project.Project", "jetbrains.mps.project.Solution", "jetbrains.mps.project.DevKit",
        "jetbrains.mps.smodel.Language", "jetbrains.mps.smodel.Generator",
        "org.jetbrains.mps.openapi.model.SNode", "org.jetbrains.mps.openapi.model.SModel",
        "org.jetbrains.mps.openapi.model.SReference", "org.jetbrains.mps.openapi.model.SNodeReference",
        "org.jetbrains.mps.openapi.model.SModelReference", "org.jetbrains.mps.openapi.module.SModule",
        "org.jetbrains.mps.openapi.module.SModuleReference", "org.jetbrains.mps.openapi.module.SearchScope",
        "org.jetbrains.mps.openapi.language.SAbstractConcept", "org.jetbrains.mps.openapi.language.SConcept",
        "org.jetbrains.mps.openapi.language.SProperty", "org.jetbrains.mps.openapi.language.SContainmentLink",
        "org.jetbrains.mps.openapi.language.SReferenceLink",
        "com.specificlanguages.mops.daemon.JavaSnippetParser", "com.specificlanguages.mops.daemon.MopsEditingBuild",
        "com.specificlanguages.mops.daemon.JavaParsingResult",
        "com.specificlanguages.mops.daemon.BuildModuleReloadResult",
        "com.specificlanguages.mops.daemon.BuildModuleReloadMessage",
        "com.specificlanguages.mops.daemon.NodeChild", "com.specificlanguages.mops.daemon.NodeChildren",
        "com.specificlanguages.mops.daemon.NodeProperties", "com.specificlanguages.mops.daemon.NodeReferences",
        "com.specificlanguages.mops.protocol.TestResult", "com.specificlanguages.mops.protocol.MakeMessageJson", "com.specificlanguages.mops.protocol.MakeOutcome", "com.specificlanguages.mops.protocol.MakeMessageKind",
        "com.specificlanguages.mops.protocol.TestRunReport", "com.specificlanguages.mops.protocol.MakeResponse",
    )

    fun resolveType(name: String): Class<*>? {
        val qualifiedName = knownTypes.singleOrNull { it.substringAfterLast('.') == name } ?: name
        return try {
            Class.forName(qualifiedName, false, CodeMemberInspector::class.java.classLoader)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    fun typeHierarchy(type: Class<*>): List<Class<*>> = buildList {
        fun visit(current: Class<*>) {
            if (current in this) return
            add(current)
            current.interfaces.forEach(::visit)
            current.superclass?.let(::visit)
        }
        visit(type)
    }

    fun inspect(type: Class<*>): CodeTypeMembers {
        val methods = type.methods.filter {
            !it.isSynthetic && !it.isBridge && it.declaringClass != Any::class.java &&
                '$' !in it.name && !it.name.matches(Regex("component\\d+"))
        }.sortedWith(compareBy({ it.name }, { it.toGenericString() }))
        val getters = methods.mapNotNull { method -> propertyName(method)?.let { it to method } }.toMap()
        val setters = methods.filter {
            !Modifier.isStatic(it.modifiers) && it.name.startsWith("set") && it.name.length > 3 &&
                it.parameterCount == 1 && it.returnType == Void.TYPE
        }.groupBy { Introspector.decapitalize(it.name.substring(3)) }
        val properties = (getters.keys + setters.keys).sorted().map { name ->
            val getter = getters[name]
            val setter = setters[name]?.firstOrNull { getter == null || it.parameterTypes[0] == getter.returnType }
            val origin = getter ?: setter ?: setters.getValue(name).first()
            CodePropertyMember(
                name, (getter?.genericReturnType ?: origin.genericParameterTypes[0]).typeName,
                getter?.name, setter?.name, origin.declaringClass.name, origin.declaringClass != type,
            )
        }
        return CodeTypeMembers(
            type.name, typeHierarchy(type).map { it.name },
            methods.map { method ->
                CodeMethodMember(
                    method.name,
                    method.parameters.map { CodeMemberParameter(if (it.isNamePresent) it.name else null, it.parameterizedType.typeName) },
                    method.genericReturnType.typeName, method.declaringClass.name,
                    method.declaringClass != type, Modifier.isStatic(method.modifiers),
                )
            },
            properties,
            type.fields.filter { !it.isSynthetic && '$' !in it.name }.sortedBy { it.name }.map {
                CodeFieldMember(it.name, it.genericType.typeName, it.declaringClass.name,
                    it.declaringClass != type, Modifier.isStatic(it.modifiers), !Modifier.isFinal(it.modifiers))
            },
        )
    }

    private fun propertyName(method: Method): String? {
        if (Modifier.isStatic(method.modifiers) || method.parameterCount != 0) return null
        return when {
            method.name.startsWith("get") && method.name.length > 3 && method.returnType != Void.TYPE ->
                Introspector.decapitalize(method.name.substring(3))
            method.name.startsWith("is") && method.name.length > 2 && method.returnType == Boolean::class.javaPrimitiveType ->
                Introspector.decapitalize(method.name.substring(2))
            else -> null
        }
    }
}
