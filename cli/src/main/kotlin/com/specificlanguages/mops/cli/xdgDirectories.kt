package com.specificlanguages.mops.cli

import java.nio.file.Path

internal fun defaultDaemonHome(
    environment: Map<String, String> = System.getenv(),
    userHome: Path = Path.of(System.getProperty("user.home")),
): Path {
    val configuredCacheHome = environment["XDG_CACHE_HOME"]
        ?.takeIf(String::isNotEmpty)
        ?.let(Path::of)
        ?.takeIf(Path::isAbsolute)
    val cacheHome = configuredCacheHome ?: userHome.resolve(".cache")
    return cacheHome.resolve("mops/daemon")
}
