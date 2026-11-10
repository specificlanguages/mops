package com.specificlanguages.mops.protocol

import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.Path

/**
 * What a daemon workspace is for: one project with one MPS home and build. The CLI writes it into the workspace when it
 * starts a daemon there, so it can find all workspaces of a project.
 */
@Serializable
data class WorkspaceMetadata(
    @Serializable(with = PathAsStringSerializer::class) val realProjectPath: Path,
    @Serializable(with = PathAsStringSerializer::class) val realMpsHome: Path,
    val mpsBuild: String,
) {
    fun writeTo(workspace: DaemonWorkspace) {
        writeAtomically(workspace.metadataPath, ProtocolJson.workspaceMetadata.encode(this))
    }

    companion object {
        fun of(context: DaemonContext) = WorkspaceMetadata(context.realProjectPath, context.realMpsHome, context.mpsBuild)

        /** The metadata of [workspace], or null when it has none. */
        fun readFrom(workspace: DaemonWorkspace): WorkspaceMetadata? {
            val path = workspace.metadataPath
            if (!Files.isRegularFile(path)) {
                return null
            }
            return ProtocolJson.workspaceMetadata.decode(Files.readString(path))
        }
    }
}
