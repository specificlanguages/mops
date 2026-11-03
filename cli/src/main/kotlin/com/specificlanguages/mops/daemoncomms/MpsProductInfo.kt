package com.specificlanguages.mops.daemoncomms

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText

/**
 * Reads how to launch the MPS runtime of an MPS home from the launch entries of its `product-info.json`, picking the
 * entry for the host platform. MPS homes without a product descriptor use every jar in `lib` and `lib/modules`.
 */
internal object MpsProductInfo {
    fun runtimeClasspath(
        mpsHome: Path,
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): List<String> {
        val descriptor = listOf(
            mpsHome.resolve("product-info.json"),
            mpsHome.resolve("Resources/product-info.json"),
        ).firstOrNull(Files::isRegularFile)

        val runtimeJars = if (descriptor == null) {
            legacyMpsRuntimeClasspath(mpsHome)
        } else {
            val launches = readMpsLaunches(descriptor)
            val platform = hostPlatform(osName, osArch, descriptor)
            selectMpsLaunch(launches, platform, descriptor).bootClassPathJarNames
                .map { mpsHome.resolve("lib").resolve(it) }
                .filter(Files::isRegularFile)
                .map(Path::pathString)
        }

        val mpsTool = mpsHome.resolve("lib/mpsant/mps-tool.jar")
        return if (mpsTool.isRegularFile() && mpsTool.pathString !in runtimeJars) {
            runtimeJars + mpsTool.pathString
        } else {
            runtimeJars
        }
    }

    fun bootstrapJvmArgs(
        mpsHome: Path,
        osName: String = System.getProperty("os.name"),
        osArch: String = System.getProperty("os.arch"),
    ): List<String> {
        val descriptor = listOf(
            mpsHome.resolve("product-info.json"),
            mpsHome.resolve("Resources/product-info.json"),
        ).firstOrNull(Files::isRegularFile) ?: return emptyList()
        val launch = selectMpsLaunch(readMpsLaunches(descriptor), hostPlatform(osName, osArch, descriptor), descriptor)
        return launch.bootstrapJvmArgs.map {
            it.replace("%IDE_HOME%", mpsHome.pathString).replace("\$IDE_HOME", mpsHome.pathString)
        }
    }

    private fun readMpsLaunches(descriptor: Path): List<MpsLaunch> =
        try {
            val launches = Json.parseToJsonElement(descriptor.readText()).jsonObject["launch"]?.jsonArray
                ?: error("missing launch array")
            require(launches.isNotEmpty()) { "launch array is empty" }
            launches.mapIndexed { index, element ->
                val launch = element.jsonObject
                val os = launch["os"]?.jsonPrimitive
                    ?.also { require(it.isString) { "launch[$index].os is not a string" } }
                    ?.contentOrNull
                    ?: error("launch[$index] is missing os")
                val arch = launch["arch"]?.jsonPrimitive?.let {
                    require(it.isString) { "launch[$index].arch is not a string" }
                    it.content
                }
                val bootClassPath = launch["bootClassPathJarNames"]?.jsonArray
                    ?: error("launch[$index] is missing bootClassPathJarNames")
                MpsLaunch(
                    os = os,
                    arch = arch,
                    bootstrapJvmArgs = launch["additionalJvmArguments"]?.jsonArray?.mapIndexed { argIndex, arg ->
                        val argument = arg.jsonPrimitive
                        require(argument.isString) { "launch[$index].additionalJvmArguments[$argIndex] is not a string" }
                        argument.content
                    }?.filter { it.startsWith("-Xbootclasspath/a:") } ?: emptyList(),
                    bootClassPathJarNames = bootClassPath.mapIndexed { jarIndex, jar ->
                        val jarName = jar.jsonPrimitive
                        require(jarName.isString) {
                            "launch[$index].bootClassPathJarNames[$jarIndex] is not a string"
                        }
                        jarName.content
                    },
                )
            }
        } catch (exception: Exception) {
            throw IllegalStateException(
                "Invalid MPS product descriptor $descriptor: ${exception.message ?: exception.javaClass.simpleName}",
                exception,
            )
        }

    private fun selectMpsLaunch(
        launches: List<MpsLaunch>,
        platform: HostPlatform,
        descriptor: Path,
    ): MpsLaunch {
        val exactMatches = launches.filter { it.os == platform.os && it.arch == platform.arch }
        if (exactMatches.size == 1) {
            return exactMatches.single()
        }
        if (launches.size == 1) {
            return launches.single()
        }

        val commonBootClasspath = launches.map { it.bootClassPathJarNames to it.bootstrapJvmArgs }.distinct()
        if (commonBootClasspath.size == 1) {
            return launches.first()
        }

        throw IllegalStateException(
            "Ambiguous launch entries in MPS product descriptor $descriptor for ${platform.os}/${platform.arch}",
        )
    }

    private fun hostPlatform(osName: String, osArch: String, descriptor: Path): HostPlatform {
        val os = when {
            osName.startsWith("Mac", ignoreCase = true) -> "macOS"
            osName.startsWith("Linux", ignoreCase = true) -> "Linux"
            osName.startsWith("Windows", ignoreCase = true) -> "Windows"
            else -> throw IllegalStateException(
                "Cannot select a launch entry from MPS product descriptor $descriptor: unsupported host OS $osName",
            )
        }
        val arch = when (osArch.lowercase()) {
            "amd64", "x86_64" -> "amd64"
            "aarch64", "arm64" -> "aarch64"
            else -> throw IllegalStateException(
                "Cannot select a launch entry from MPS product descriptor $descriptor: unsupported host architecture $osArch",
            )
        }
        return HostPlatform(os, arch)
    }

    private fun legacyMpsRuntimeClasspath(mpsHome: Path): List<String> =
        buildList {
            addAll(jarsIn(mpsHome.resolve("lib")))
            addAll(jarsIn(mpsHome.resolve("lib/modules")))
        }

    private fun jarsIn(directory: Path): List<String> {
        if (!Files.isDirectory(directory)) {
            return emptyList()
        }
        return Files.list(directory).use { entries ->
            entries
                .filter { Files.isRegularFile(it) && it.extension == "jar" }
                .map { it.pathString }
                .sorted()
                .toList()
        }
    }

    private data class HostPlatform(val os: String, val arch: String)

    private data class MpsLaunch(
        val os: String,
        val arch: String?,
        val bootClassPathJarNames: List<String>,
        val bootstrapJvmArgs: List<String>,
    )
}
