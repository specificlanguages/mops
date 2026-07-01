package com.specificlanguages.mops.daemon

import jetbrains.mps.project.Project
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SaveOptions
import org.jetbrains.mps.openapi.model.SaveResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * Runs a mutating [body] inside MPS's write context: an EDT command wrapping a write action, with
 * the result bridged back to the calling thread. A throwable raised inside the write action is
 * rethrown to the caller, unwrapped from the [ExecutionException] the bridge would otherwise wrap
 * it in.
 *
 * Inside the body, use [WriteScope.asEditable] to guard that a model may be written and
 * [WriteScope.saveWithResolveInfo] to persist the affected models.
 */
class WriteTransaction {
    fun <T> run(project: Project, body: WriteScope.() -> T): T {
        val future = CompletableFuture<T>()

        project.modelAccess.executeCommandInEDT {
            try {
                future.complete(project.modelAccess.computeWriteAction { WriteScope.body() })
            } catch (t: Throwable) {
                future.completeExceptionally(t)
            }
        }

        return try {
            future.get()
        } catch (exception: ExecutionException) {
            throw exception.cause ?: exception
        }
    }

    /** Operations available to a [run] body while inside the write action. */
    object WriteScope {
        /**
         * Returns [model] typed as an [EditableSModel] if it may be written, or `null` if it is
         * read-only or not an editable model at all. Guard with this before mutating or saving.
         */
        fun asEditable(model: SModel): EditableSModel? =
            if (model.isReadOnly || model !is EditableSModel) null else model

        /**
         * Saves each of [models] with resolve info. Stops at the first model that fails to save and
         * reports it; returns [SaveOutcome.Saved] when every model was persisted.
         */
        fun saveWithResolveInfo(models: Iterable<EditableSModel>): SaveOutcome {
            for (model in models) {
                val result = model.save(SaveOptions.FORCE_SAVE_WITH_RESOLVE_INFO).toCompletableFuture().join()
                if (result != SaveResult.SAVED_TO_DATA_SOURCE && result != SaveResult.NOT_CHANGED) {
                    return SaveOutcome.SaveFailed(model, result)
                }
            }
            return SaveOutcome.Saved
        }
    }
}

/** The result of saving a set of models with resolve info. */
sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data class SaveFailed(val model: SModel, val result: SaveResult) : SaveOutcome
}
