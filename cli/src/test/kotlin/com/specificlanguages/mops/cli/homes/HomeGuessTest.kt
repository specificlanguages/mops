package com.specificlanguages.mops.cli.homes

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.*

class HomeGuessTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `selection prefers closest complete pair and never combines partial sources`() {
        val mps = root.resolve("mps").createDirectory()
        val java = Path.of(System.getProperty("java.home"))
        val parent = HomeGuess(root, ": mpsDefaults", mps, java, emptyList())
        val child = parent.copy(projectDir = root.resolve("child"), source = ":child:build")
        val partial = child.copy(source = ":child:a", javaHome = null)
        val discovery = GradleHomeDiscovery()
        assertEquals(child, discovery.select(listOf(partial, parent, child), root.resolve("child/nested")))
        assertEquals(parent, discovery.select(listOf(partial, parent), root.resolve("child/nested")))
        assertEquals(partial, discovery.select(listOf(partial), root.resolve("child")))
    }

    @Test
    fun `report preserves partial results and diagnostics`() {
        val text = """{"version":1,"candidates":[{"projectDir":"/project","source":":build","mpsHome":"/mps","javaHome":null,"diagnostics":["provider failed"]}]}"""
        assertEquals(
            listOf(HomeGuess(Path.of("/project"), ":build", Path.of("/mps"), null, listOf("provider failed"))),
            GradleHomeDiscovery().parseReport(text),
        )
    }
}
