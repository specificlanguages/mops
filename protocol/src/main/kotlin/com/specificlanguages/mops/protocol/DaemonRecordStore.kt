package com.specificlanguages.mops.protocol

import java.nio.file.Files
import java.nio.file.Files.move
import java.nio.file.Files.writeString
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString

/**
 * Filesystem-backed registry of daemon records.
 *
 * Records live outside the MPS project under the daemon state directory, one per daemon workspace. The CLI finds, reuses,
 * stops, or discards daemons by their workspace without scanning process tables.
 */
class DaemonRecordStore(val paths: DaemonPaths) {
    /** Writes [record] and the metadata of its workspace, the way the CLI and a started daemon do together. */
    fun write(record: DaemonRecord) {
        val workspace = paths.workspace(record.context)
        WorkspaceMetadata.of(record.context).writeTo(workspace)
        workspace.writeDaemonRecord(record)
    }

    /** The record of the daemon in the workspace of [context], whatever context that daemon was started with. */
    fun read(context: DaemonContext): StoredDaemonRecord? = paths.workspace(context).readDaemonRecord()

    /** The records of the daemons in all workspaces of the project at [projectPath]. */
    fun readForProject(projectPath: Path): List<StoredDaemonRecord> =
        paths.workspacesOf(projectPath).mapNotNull { it.readDaemonRecord() }

    fun readAll(): List<StoredDaemonRecord> {
        val projectsDir = paths.projects
        if (!Files.isDirectory(projectsDir)) {
            return emptyList()
        }
        return Files.list(projectsDir).use { projects ->
            projects
                .map { it.resolve("daemon.json") }
                .filter { Files.isRegularFile(it) }
                .map {
                    StoredDaemonRecord(
                        it,
                        ProtocolJson.record.decode(Files.readString(it))
                    )
                }
                .toList()
        }
    }

    fun deleteRecord(recordPath: Path) {
        Files.deleteIfExists(recordPath)
    }

    fun recordPath(context: DaemonContext): Path =
        paths.workspace(context).recordPath()

    fun workspacePath(context: DaemonContext): Path =
        paths.workspace(context).path

    fun checkRecordWasWritten(context: DaemonContext) {
        read(context)
            ?: throw IllegalStateException("daemon did not write its record under ${recordPath(context)}")
    }

    companion object {
        fun forDaemonHome(path: Path) = DaemonRecordStore(DaemonPaths(path))
    }
}

/**
 * The directories under a daemon home. Each daemon workspace belongs to one project and one MPS home and build, so a
 * project used with two MPS versions has two workspaces, each with its own IntelliJ directories.
 */
class DaemonPaths(root: Path) {
    val projects: Path = root.resolve("projects")

    fun workspace(context: DaemonContext): DaemonWorkspace =
        DaemonWorkspace(projects.resolve(workspaceKey(context)))

    /** The workspaces of the project at [projectPath], found by their [WorkspaceMetadata]. */
    fun workspacesOf(projectPath: Path): List<DaemonWorkspace> {
        if (!Files.isDirectory(projects)) {
            return emptyList()
        }
        val realProjectPath = projectPath.toRealPath()
        return Files.list(projects).use { directories ->
            directories.map(::DaemonWorkspace)
                .filter { WorkspaceMetadata.readFrom(it)?.realProjectPath == realProjectPath }
                .toList()
        }
    }

    private fun workspaceKey(context: DaemonContext): String =
        sha256(listOf(context.realProjectPath.pathString, context.realMpsHome.pathString, context.mpsBuild).joinToString("\n"))

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}

class DaemonWorkspace(val path: Path) {
    /** The file that holds the [WorkspaceMetadata] of this workspace. */
    val metadataPath: Path = path.resolve("workspace.json")

    fun recordPath(): Path = path.resolve("daemon.json")

    fun writeDaemonRecord(record: DaemonRecord) {
        writeAtomically(recordPath(), ProtocolJson.record.encode(record))
    }

    fun readDaemonRecord(): StoredDaemonRecord? {
        val path = recordPath()
        if (!Files.isRegularFile(path)) {
            return null
        }
        return StoredDaemonRecord(path, ProtocolJson.record.decode(Files.readString(path)))
    }

    /**
     * Removes the daemon record only if the one currently on disk still belongs to [token]. A daemon calls this as it
     * exits so it does not leave a dangling record behind; the token guard prevents it from deleting a record a newer
     * daemon has since written for the same project.
     */
    fun deleteDaemonRecordOwnedBy(token: String) {
        if (readDaemonRecord()?.record?.token == token) {
            Files.deleteIfExists(recordPath())
        }
    }

    fun daemonWorkingDir(): Path = path.resolve("daemon")
    fun ideaConfigDir(): Path = daemonWorkingDir().resolve("config")
    fun ideaSystemDir(): Path = daemonWorkingDir().resolve("system")
    fun logDir(): Path = path.resolve("logs")
    fun logFile(): Path = logDir().resolve("daemon.log")

    /** The IntelliJ system directory (VFS, indexes, caches) that the test worker keeps between test runs. */
    fun testWorkerSystemDir(): Path = path.resolve("test-worker-system")

    fun createDirectories() {
        daemonWorkingDir().createDirectories()
        ideaConfigDir().createDirectories()
        ideaSystemDir().createDirectories()
        logDir().createDirectories()
    }
}

/** Replaces [target] with [text] in one step, so a reader never sees a partly written file. */
internal fun writeAtomically(target: Path, text: String) {
    target.parent.createDirectories()
    val temporary = target.resolveSibling("${target.fileName}.tmp")
    writeString(temporary, text)
    move(
        temporary,
        target,
        StandardCopyOption.ATOMIC_MOVE,
        StandardCopyOption.REPLACE_EXISTING,
    )
}
