package com.specificlanguages.mops.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class XdgDirectoriesTest {
    private val userHome = Path.of("/home/example")

    @Test
    fun `daemon home uses XDG cache home`() {
        val daemonHome = defaultDaemonHome(
            environment = mapOf("XDG_CACHE_HOME" to "/var/cache/example"),
            userHome = userHome,
        )

        assertEquals(Path.of("/var/cache/example/mops/daemon"), daemonHome)
    }

    @Test
    fun `daemon home defaults to cache below user home`() {
        val daemonHome = defaultDaemonHome(environment = emptyMap(), userHome = userHome)

        assertEquals(Path.of("/home/example/.cache/mops/daemon"), daemonHome)
    }

    @Test
    fun `daemon home ignores empty or relative XDG cache home`() {
        assertEquals(
            Path.of("/home/example/.cache/mops/daemon"),
            defaultDaemonHome(mapOf("XDG_CACHE_HOME" to ""), userHome),
        )
        assertEquals(
            Path.of("/home/example/.cache/mops/daemon"),
            defaultDaemonHome(mapOf("XDG_CACHE_HOME" to "relative/cache"), userHome),
        )
    }
}
