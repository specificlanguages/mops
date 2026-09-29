import java.util.zip.ZipFile

plugins {
    id("mops.kotlin-jvm-conventions")

    alias(libs.plugins.mps.platform.cache)
}

val mpsZip = configurations.register("mpsZip") { isCanBeConsumed = false }
val mpsRuntime = configurations.register("mpsRuntime") { isCanBeConsumed = false }

configurations.compileOnly { extendsFrom(mpsRuntime) }

dependencies {
    mpsRuntime(mpsZip.map {
        zipTree(it.singleFile).matching {
            include("lib/mps-core.jar")
            include("lib/mps-openapi.jar")
            include("lib/mps-persistence.jar")
            include("lib/util.jar")
            include("plugins/mps-java/lib/java-core.jar")
            include("plugins/java/lib/ecj/eclipse.jar")
        }
    })
    mpsZip(libs.mps.distribution)
}

val checkPluginJar = tasks.register("checkPluginJar") {
    val pluginJar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(pluginJar)

    doLast {
        val allowedEntries = listOf(
            "META-INF/",
            "META-INF/MANIFEST.MF",
            "META-INF/plugin.xml",
            "com/",
            "com/specificlanguages/",
            "com/specificlanguages/mops/",
            "com/specificlanguages/mops/daemon/",
            "com/specificlanguages/mops/daemon/plugin/",
        )
        ZipFile(pluginJar.get().asFile).use { jar ->
            val unexpected = jar.entries().asSequence()
                .map { it.name }
                .filter { entry ->
                    entry !in allowedEntries &&
                        !entry.startsWith("com/specificlanguages/mops/daemon/plugin/") &&
                        !(entry.startsWith("META-INF/") && entry.endsWith(".kotlin_module"))
                }
                .toList()
            require(unexpected.isEmpty()) {
                "Plugin JAR contains files outside its own classes and resources: ${unexpected.joinToString()}"
            }
            require(jar.getEntry("META-INF/plugin.xml") != null) { "Plugin JAR has no META-INF/plugin.xml" }
        }
    }
}

tasks.check { dependsOn(checkPluginJar) }
