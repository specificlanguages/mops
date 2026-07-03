package com.specificlanguages.mops.protocol

/**
 * Outcome of a notation-level, two-pass decode of an edit batch (see [ProtocolJson.decodeBatchOrError]).
 *
 * Either the batch decoded cleanly, or a single [Failure] pinpoints the first notation problem. A failure carries both
 * the structured coordinates (which operation, which op kind, which [BatchDecodeErrorCategory]) and a fully rendered
 * [detail] line that already names what is wrong, where, the expected shape, and the `mops explain` page to read — so
 * the CLI surfaces it verbatim without touching serialization types.
 */
sealed interface BatchDecodeResult {
    data class Success(val batch: EditBatch) : BatchDecodeResult

    data class Failure(
        val operationIndex: Int?,
        val opKind: String?,
        val category: BatchDecodeErrorCategory,
        val detail: String,
    ) : BatchDecodeResult
}

/**
 * Classifies a notation-level batch parse error. Batch-level shape problems carry a null operation index; every other
 * category pins a specific `operations[i]`.
 */
enum class BatchDecodeErrorCategory {
    BatchShape,
    UnknownOp,
    MissingField,
    UnknownField,
    InvalidTarget,
    InvalidPosition,
    WrongType,
}
