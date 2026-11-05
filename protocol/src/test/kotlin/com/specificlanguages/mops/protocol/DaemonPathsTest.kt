package com.specificlanguages.mops.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DaemonPathsTest {
    @TempDir
    lateinit var tempDir: Path

    private val paths by lazy { DaemonPaths(tempDir.resolve("daemon-home")) }
    private val javaHome = Path.of(System.getProperty("java.home"))

    private fun directory(name: String): Path = Files.createDirectories(tempDir.resolve(name))

    private fun mpsHome(name: String, build: String): Path =
        directory(name).also { Files.writeString(it.resolve("build.properties"), "mps.build.number=$build\n") }

    private fun context(project: Path, mpsHome: Path, macros: Map<String, Path> = emptyMap()) =
        DaemonContext.fromLivePaths(project, mpsHome, javaHome, macros)

    @Test
    fun `a project gets one workspace per MPS home and build`() {
        val project = directory("project")
        val mps = mpsHome("mps", "MPS-241.1.1")
        val first = paths.workspace(context(project, mps)).path

        assertEquals(first, paths.workspace(context(project, mps, mapOf("repo" to directory("repo")))).path)
        assertNotEquals(first, paths.workspace(context(project, mpsHome("other-mps", "MPS-241.1.1"))).path)
        assertNotEquals(first, paths.workspace(context(directory("other-project"), mps)).path)

        mpsHome("mps", "MPS-242.1.1")
        assertNotEquals(first, paths.workspace(context(project, mps)).path)
        mpsHome("mps", "MPS-241.1.1")
        assertEquals(first, paths.workspace(context(project, mps)).path)
    }

    @Test
    fun `the workspaces of a project are found by their metadata`() {
        val project = directory("project")
        val contexts = listOf(context(project, mpsHome("a", "MPS-241.1.1")), context(project, mpsHome("b", "MPS-251.1.1")))
        val otherProject = context(directory("other-project"), mpsHome("a", "MPS-241.1.1"))
        for (context in contexts + otherProject) {
            WorkspaceMetadata.of(context).writeTo(paths.workspace(context))
        }
        Files.createDirectories(paths.projects.resolve("without-metadata"))

        assertEquals(
            contexts.map { paths.workspace(it).path }.toSet(),
            paths.workspacesOf(tempDir.resolve("project/../project")).map { it.path }.toSet(),
        )
        assertEquals(WorkspaceMetadata.of(contexts[1]), WorkspaceMetadata.readFrom(paths.workspace(contexts[1])))
    }
}
