package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsErrorCode
import com.specificlanguages.mops.daemon.core.MpsRequestException
import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.persistence.PersistenceFacade

object MopsLookupExtensions {
    private val persistence get() = PersistenceFacade.getInstance()

    /** Resolves a module name or serialized reference, returning null on a miss and rejecting ambiguity. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun module(lookup: MopsLookup, target: String): SModule? {
        requireRead(lookup.mops, "mops.lookup.module")
        val matches = lookup.mops.project.repository.modules.filter {
            it.moduleName == target || persistence.asString(it.moduleReference) == target
        }
        require(matches.size <= 1) {
            "ambiguous module: $target; candidates: ${matches.joinToString { persistence.asString(it.moduleReference) }}"
        }
        return matches.singleOrNull()
    }

    /** Resolves an established model target, returning null on a miss and rejecting ambiguity. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun model(lookup: MopsLookup, target: String): SModel? {
        requireRead(lookup.mops, "mops.lookup.model")
        return ModelNodeResolver(DaemonLogger()).findModelUnique(lookup.mops.project, target)
    }

    /** Resolves a node reference, returning null on a miss and rejecting malformed references. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun node(lookup: MopsLookup, reference: String): SNode? {
        requireRead(lookup.mops, "mops.lookup.node")
        val parsed = runCatching { persistence.createNodeReference(reference) }.getOrNull()
            ?: throw IllegalArgumentException("invalid node reference: $reference")
        return parsed.resolve(lookup.mops.project.repository)
    }

    /** Resolves a concept name, returning null on a miss. Ambiguity and untrusted runtimes remain errors. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun conceptByName(lookup: MopsLookup, name: String): SAbstractConcept? {
        requireRead(lookup.mops, "mops.lookup.conceptByName")
        require(name.isNotBlank() && (!name.contains('.') || ConceptName.parse(name) != null)) {
            ConceptResolver.malformedMessage(name)
        }
        return try {
            ConceptResolver(lookup.mops.project).resolve(name)
        } catch (failure: MpsRequestException) {
            if (failure.code != MpsErrorCode.CONCEPT_NOT_FOUND) throw failure
            null
        }
    }

    /** Resolves exactly one module, throwing on a miss or ambiguity. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun requireModule(lookup: MopsLookup, target: String): SModule =
        module(lookup, target) ?: throw IllegalArgumentException("module not found: $target")

    /** Resolves exactly one model, throwing on a miss or ambiguity. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun requireModel(lookup: MopsLookup, target: String): SModel =
        model(lookup, target) ?: throw IllegalArgumentException("model not found: $target")

    /** Resolves exactly one node reference, throwing on a miss. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun requireNode(lookup: MopsLookup, reference: String): SNode =
        node(lookup, reference) ?: throw IllegalArgumentException("node not found: $reference")

    /** Resolves exactly one concept name, throwing with diagnostics on a miss. Access: read. */
    @CodeModeExtension(MpsAccessLevel.READ)
    @JvmStatic
    fun requireConceptByName(lookup: MopsLookup, name: String): SAbstractConcept {
        requireRead(lookup.mops, "mops.lookup.requireConceptByName")
        return ConceptResolver(lookup.mops.project).resolve(name)
    }
}
