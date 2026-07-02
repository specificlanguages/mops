package com.specificlanguages.mops.protocol

/**
 * Ordered batch of Edit Operations applied atomically by `mops model edit`.
 */
data class EditBatch(
    val operations: List<EditOperation>,
)

sealed interface EditOperation {
    val op: String

    data class SetProperty(
        val target: EditTarget,
        val name: String,
        val value: String? = null,
    ) : EditOperation {
        override val op: String = "setProperty"
    }
}

sealed interface EditTarget {
    data class NodeReference(val nodeReference: String) : EditTarget
    data class InModel(val modelTarget: String, val nodeId: String) : EditTarget
    data class Alias(val alias: String) : EditTarget
}

data class EditConstraintViolation(
    val operation: Int,
    val constraint: String,
    val message: String,
)
