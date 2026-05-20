package com.specificlanguages.mops.cli

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.jvm.javaClass
import kotlin.use

fun copyTestProject(name: String, target: Path): Path {
    val source = Path.of(
        requireNotNull(DummyForClassLoader.javaClass.classLoader.getResource("test-projects/$name")) {
            "missing test project resource test-projects/$name"
        }.toURI(),
    )
    target.createDirectories()
    copyDirectory(source, target)
    return target
}

private object DummyForClassLoader {}

private fun copyDirectory(source: Path, target: Path) {
    target.createDirectories()
    Files.walk(source).use { paths ->
        paths.forEach { path ->
            val destination = target.resolve(source.relativize(path).pathString)
            if (Files.isDirectory(path)) {
                destination.createDirectories()
            } else {
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun requiredProperty(name: String): String =
    requireNotNull(System.getProperty(name)) { "missing system property $name" }

public fun javaAndMpsHomeArgs(): Array<String> =
    arrayOf(
        "--java-home",
        requiredProperty("test.jbrHome"),
        "--mps-home",
        requiredProperty("test.mpsHome")
    )