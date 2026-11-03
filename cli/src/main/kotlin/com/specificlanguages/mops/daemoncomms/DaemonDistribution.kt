package com.specificlanguages.mops.daemoncomms

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

/**
 * The daemon jars and MPS plugin of the mops installation in [applicationHome]. The JVM properties
 * `mops.daemon.classpath` and [DAEMON_MPS_PLUGIN_PROPERTY] override them.
 */
class DaemonDistribution(private val applicationHome: Path? = discoverApplicationHome()) {
    /**
     * The daemon classpath entries to put in front of [mpsClasspath]. MPS can bundle a complete Groovy distribution,
     * including extension modules compiled against its core. That runtime is used as a unit; the mops Groovy
     * dependency is a fallback for MPS distributions without Groovy.
     */
    fun daemonClasspath(mpsClasspath: List<String>): List<String> {
        val daemonClasspath = configuredClasspath().split(File.pathSeparator).filter { it.isNotBlank() }
        return if (mpsClasspath.any(::providesGroovyCompiler)) {
            daemonClasspath.filterNot(::providesGroovyCompiler)
        } else {
            daemonClasspath
        }
    }

    fun mpsPlugin(): Path {
        val configured = System.getProperty(DAEMON_MPS_PLUGIN_PROPERTY)?.let(Path::of)
        val plugin = configured ?: applicationHome?.resolve("mps-plugins")?.resolve(DAEMON_MPS_PLUGIN_FILE)
            ?: throw IllegalStateException(
                "Could not determine application home, set $DAEMON_MPS_PLUGIN_PROPERTY JVM property explicitly",
            )
        require(plugin.isRegularFile()) { "MOPS daemon plugin is missing: ${plugin.toAbsolutePath().normalize()}" }
        return plugin.toAbsolutePath().normalize()
    }

    private fun configuredClasspath(): String = System.getProperty("mops.daemon.classpath") ?: distributionClasspath()

    private fun distributionClasspath(): String {
        val home = applicationHome
            ?: throw IllegalStateException("Could not determine application home, set mops.daemon.classpath JVM property explicitly")
        val classpathFile = home.resolve("lib").resolve(DAEMON_CLASSPATH_FILE)
        if (!Files.isRegularFile(classpathFile)) {
            throw IllegalStateException("File lib/${DAEMON_CLASSPATH_FILE} is missing, set mops.daemon.classpath JVM property explicitly")
        }

        return Files.readAllLines(classpathFile)
            .asSequence()
            .map { it.substringBefore("#").trim() }
            .filter { it.isNotEmpty() }
            .map { entry -> home.resolve(entry) }
            .joinToString(File.pathSeparator) { it.normalize().pathString }
    }

    private fun providesGroovyCompiler(entry: String): Boolean {
        val file = File(entry)
        return file.isFile && file.extension == "jar" && JarFile(file).use {
            it.getJarEntry("org/codehaus/groovy/control/CompilerConfiguration.class") != null
        }
    }

    companion object {
        private const val DAEMON_CLASSPATH_FILE = "mops-daemon.classpath"
        private const val DAEMON_MPS_PLUGIN_FILE = "mops-daemon-plugin.jar"
        internal const val DAEMON_MPS_PLUGIN_PROPERTY = "mops.daemon.mps.plugin"

        private fun discoverApplicationHome(): Path? {
            val location = DaemonDistribution::class.java.protectionDomain.codeSource?.location
                ?: return null
            val codeSource = runCatching { Path.of(location.toURI()).toAbsolutePath().normalize() }.getOrNull()
                ?: return null
            val libDir =
                if (Files.isRegularFile(codeSource)) codeSource.parent
                else codeSource
            return libDir?.parent
        }
    }
}
