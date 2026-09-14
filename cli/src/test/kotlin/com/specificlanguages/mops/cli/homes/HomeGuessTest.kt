package com.specificlanguages.mops.cli.homes

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.*

class HomeGuessTest {
    @TempDir
    lateinit var temporary: Path

    @Test
    fun `report preserves a partial result`() {
        val text = """{"version":1,"candidate":{"projectDir":"/project","buildDir":"/project/out","source":":build","mpsHome":"/mps","javaHome":null,"mpsProjectRoot":"/mps-project"}}"""
        assertEquals(
            HomeGuess(
                Path.of("/project"), Path.of("/project/out"), ":build",
                Path.of("/mps"), null, Path.of("/mps-project")
            ),
            GradleHomeDiscovery().parseReport(text),
        )
    }

    @Test
    fun `wrappers pass configured paths and caller arguments`() {
        val guess = HomeGuess(
            projectDir = temporary,
            buildDir = temporary.resolve("build dir"),
            source = ": mpsDefaults",
            mpsHome = Path.of("/MPS home's"),
            javaHome = Path.of("/Java home"),
            mpsProjectRoot = Path.of("/MPS project"),
        )

        assertEquals(
            "#!/bin/sh\nexec mops --mps-home='/MPS home'\"'\"'s' --java-home='/Java home' --project-root='/MPS project' \"\$@\"\n",
            guess.posixWrapper(),
        )
        assertEquals(
            "@echo off\r\nmops --mps-home=\"/MPS home's\" --java-home=\"/Java home\" --project-root=\"/MPS project\" %*\r\n",
            guess.windowsWrapper(),
        )
        val wrapper = temporary.resolve("custom/wrapper.cmd")
        assertEquals(wrapper, guess.writeWrapper(wrapper, windows = true))
    }

    @Test
    fun `POSIX wrapper forwards its arguments`() {
        val bin = temporary.resolve("bin").createDirectories()
        val mops = bin.resolve("mops")
        mops.writeText("#!/bin/sh\nprintf '%s\\n' \"\$@\"\n")
        assertTrue(mops.toFile().setExecutable(true))
        val guess = HomeGuess(
            projectDir = temporary,
            buildDir = temporary.resolve("build"),
            source = ": mpsDefaults",
            mpsHome = Path.of("/MPS home"),
            javaHome = Path.of("/Java home"),
            mpsProjectRoot = Path.of("/MPS project"),
        )
        val wrapper = guess.writeWrapper(temporary.resolve("custom/mopsw"), windows = false)
        val process = ProcessBuilder(wrapper.toString(), "find", "things with spaces")
            .redirectErrorStream(true)
            .apply { environment()["PATH"] = "$bin:${environment()["PATH"]}" }
            .start()

        assertEquals(
            listOf(
                "--mps-home=/MPS home",
                "--java-home=/Java home",
                "--project-root=/MPS project",
                "find",
                "things with spaces",
            ),
            process.inputStream.bufferedReader().readLines(),
        )
        assertEquals(0, process.waitFor())
    }
}
