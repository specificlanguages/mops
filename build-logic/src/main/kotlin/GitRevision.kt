import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * The abbreviated commit of the git checkout at [Parameters.rootDirectory], with a `-dirty` suffix when tracked files
 * have uncommitted changes. Absent when the directory is not the root of a git checkout or git cannot run.
 */
abstract class GitRevision : ValueSource<String, GitRevision.Parameters> {
    interface Parameters : ValueSourceParameters {
        val rootDirectory: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): String? {
        val directory = parameters.rootDirectory.get().asFile
        val output = ByteArrayOutputStream()
        val result = try {
            execOperations.exec {
                // --exclude=* ignores tags, so --always falls back to the abbreviated commit.
                commandLine("git", "describe", "--always", "--dirty", "--exclude=*")
                workingDir = directory
                // Stop git from finding an enclosing repository when this source tree has no .git of its own.
                environment("GIT_CEILING_DIRECTORIES", directory.parentFile.absolutePath)
                // Do not lock the index for refreshing it, so a concurrent git command in the checkout is not blocked.
                environment("GIT_OPTIONAL_LOCKS", "0")
                standardOutput = output
                errorOutput = OutputStream.nullOutputStream()
                isIgnoreExitValue = true
            }
        } catch (_: GradleException) {
            return null
        }
        return output.toString().trim().takeIf { result.exitValue == 0 && it.isNotEmpty() }
    }
}
