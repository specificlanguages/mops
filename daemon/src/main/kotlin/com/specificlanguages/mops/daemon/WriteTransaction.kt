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
 * unwrapped and rethrown to the caller.
 *
 * Inside the body, affected editable models are persisted through [WriteScope.saveWithResolveInfo].
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
         * Saves each of [models] with resolve info, checking first that it is an editable, writable
         * model. Stops at the first model that is not editable or fails to save and reports it;
         * returns [SaveOutcome.Saved] when every model was persisted.
         */
        fun saveWithResolveInfo(models: Iterable<SModel>): SaveOutcome {
            for (model in models) {
                if (model.isReadOnly || model !is EditableSModel) {
                    return SaveOutcome.NotEditable(model)
                }

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
    data class NotEditable(val model: SModel) : SaveOutcome
    data class SaveFailed(val model: SModel, val result: SaveResult) : SaveOutcome
}
