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
 * Records live outside the MPS project under the daemon state directory. The project path is hashed into the directory
 * name so the CLI can find, reuse, stop, or discard project-specific daemons without scanning process tables.
 */
class DaemonRecordStore(val paths: DaemonPaths) {
    fun write(record: DaemonRecord) {
        val projectPath = record.context.realProjectPath
        paths.workspace(projectPath).writeDaemonRecord(record)
    }

    fun read(projectPath: Path): StoredDaemonRecord? = paths.workspace(projectPath).readDaemonRecord()

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
                        ProtocolJson.decodeRecord(Files.readString(it))
                    )
                }
                .toList()
        }
    }

    fun deleteRecord(recordPath: Path) {
        Files.deleteIfExists(recordPath)
    }

    fun recordPath(projectPath: Path): Path =
        paths.workspace(projectPath).recordPath()

    fun workspacePath(projectPath: Path): Path =
        paths.workspace(projectPath).path

    fun checkRecordWasWrittenForProject(realProjectPath: Path) {
        read(realProjectPath)
            ?: throw IllegalStateException("daemon did not write its project record under ${recordPath(realProjectPath)}")
    }

    companion object {
        fun forDaemonHome(path: Path) = DaemonRecordStore(DaemonPaths(path))
    }
}

class DaemonPaths(root: Path) {
    val projects: Path = root.resolve("projects")

    fun workspace(projectPath: Path): DaemonWorkspace =
        DaemonWorkspace(workspacePath(projectPath))

    fun workspacePath(projectPath: Path): Path =
        projects.resolve(projectKey(projectPath))

    fun projectKey(projectPath: Path): String =
        sha256(projectPath.toRealPath().pathString)

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }
}

class DaemonWorkspace(val path: Path) {
    fun recordPath(): Path = path.resolve("daemon.json")

    fun writeDaemonRecord(record: DaemonRecord) {
        val recordPath = recordPath()
        recordPath.parent.createDirectories()
        val temporary = recordPath.resolveSibling("${recordPath.fileName}.tmp")
        writeString(temporary, ProtocolJson.encodeRecord(record))
        move(
            temporary,
            recordPath,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }

    fun readDaemonRecord(): StoredDaemonRecord? {
        val path = recordPath()
        if (!Files.isRegularFile(path)) {
            return null
        }
        return StoredDaemonRecord(path, ProtocolJson.decodeRecord(Files.readString(path)))
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

    fun createDirectories() {
        daemonWorkingDir().createDirectories()
        ideaConfigDir().createDirectories()
        ideaSystemDir().createDirectories()
        logDir().createDirectories()
    }
}
