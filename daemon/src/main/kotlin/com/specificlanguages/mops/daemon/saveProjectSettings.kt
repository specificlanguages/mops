package com.specificlanguages.mops.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import jetbrains.mps.project.MPSProject

/**
 * Saves dirty project settings. Must be called with no model read or write action active.
 *
 * Project.save() does nothing while the application disallows saving, which is the default for a headless application.
 * Platforms before 2024.3 (243) ignore -Dallow.save.application.headless, so saving is allowed explicitly for the
 * duration of the save.
 */
internal fun saveProjectSettings(project: MPSProject) {
    ApplicationManager.getApplication().invokeAndWait {
        val application = ApplicationManagerEx.getApplicationEx()
        val saveAllowed = application.isSaveAllowed
        application.isSaveAllowed = true
        try {
            project.save()
        } finally {
            application.isSaveAllowed = saveAllowed
        }
    }
}
