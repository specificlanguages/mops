package com.specificlanguages.mops.daemon

import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SRepository

/**
 * Reloads each changed editable model among [models] from its source, dropping its unsaved in-memory changes so memory
 * matches disk again. Unchanged models are left alone. A model that fails to reload does not stop the others; the
 * failures are returned. Requires write access.
 */
internal fun discardUnsavedChanges(models: Iterable<SModel>): List<Throwable> =
    models.filterIsInstance<EditableSModel>().filter { it.isChanged }.mapNotNull { model ->
        runCatching { model.reloadFromSource() }.exceptionOrNull()
    }

/** Applies [discardUnsavedChanges] to every model of every module in [repository]. Requires write access. */
internal fun discardUnsavedChanges(repository: SRepository): List<Throwable> =
    discardUnsavedChanges(repository.modules.flatMap { it.models })
