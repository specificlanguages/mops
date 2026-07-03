import java.util.zip.ZipFile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Used by checkDaemonRelocation to walk live class references without tripping over dead constant-pool strings.
        classpath("org.ow2.asm:asm:9.7.1")
        classpath("org.ow2.asm:asm-commons:9.7.1")
    }
}

plugins {
    id("mops.kotlin-jvm-conventions")
    application

    id("com.specificlanguages.mps-platform-cache") version "1.0.0"
    id("com.specificlanguages.jbr-toolchain") version "1.0.2"
}

val mpsZip: Configuration by configurations.creating
val mpsRuntime: Configuration by configurations.creating

configurations {
    compileOnly { extendsFrom(mpsRuntime) }
    testCompileOnly { extendsFrom(mpsRuntime) }
}

dependencies {
    implementation(project(":daemon-core"))
    implementation(project(":protocol"))
    implementation(project(":launcher"))
    implementation("info.picocli:picocli:4.7.7")
    implementation("de.itemis.mps.build-backends:project-loader:5.0.1.180.8e0fd7e")

    jbr("com.jetbrains.jdk:jbr_jcef:21.0.8-b895.146")

    testImplementation("com.github.stefanbirkner:system-lambda:1.2.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")

    mpsRuntime(zipTree({ mpsZip.singleFile }).matching {
        include("lib/mps-core.jar")
        include("lib/mps-collections.jar")
        include("lib/mps-closures.jar")
        include("lib/mps-environment.jar")
        include("lib/mps-persistence.jar")
        include("lib/mps-platform.jar")
        include("lib/mps-openapi.jar")
        include("lib/mps-references.jar")
        include("lib/mps-constraints-runtime.jar")
        include("lib/util.jar")
        include("lib/util-8.jar")
        include("lib/util_rt.jar")
        include("lib/testFramework.jar")
        include("lib/app.jar")
    })
    mpsZip("com.jetbrains:mps:2025.1.2")
}

application {
    applicationName = "mops-daemon"
    mainClass = "com.specificlanguages.mops.daemon.MainKt"
}

val testMpsRoot = mpsPlatformCache.getMpsRoot(configurations.named("mpsZip"))

// Keep in sync with MpsLaunchArgs.MPS_ADD_OPENS in the launcher project: --add-opens must be a JVM launch
// argument, so the test JVM cannot apply the launcher's list at runtime the way it applies -D properties.
val mpsAddOpens = listOf(
    "java.base/java.io",
    "java.base/java.lang",
    "java.base/java.lang.reflect",
    "java.base/java.net",
    "java.base/java.nio",
    "java.base/java.nio.charset",
    "java.base/java.text",
    "java.base/java.time",
    "java.base/java.util",
    "java.base/java.util.concurrent",
    "java.base/java.util.concurrent.atomic",
    "java.base/jdk.internal.ref",
    "java.base/jdk.internal.vm",
    "java.base/sun.nio.ch",
    "java.base/sun.nio.fs",
    "java.base/sun.security.ssl",
    "java.base/sun.security.util",
    "java.desktop/java.awt",
    "java.desktop/java.awt.dnd.peer",
    "java.desktop/java.awt.event",
    "java.desktop/java.awt.image",
    "java.desktop/java.awt.peer",
    "java.desktop/javax.swing",
    "java.desktop/javax.swing.plaf.basic",
    "java.desktop/javax.swing.text.html",
    "java.desktop/sun.awt.datatransfer",
    "java.desktop/sun.awt.image",
    "java.desktop/sun.awt",
    "java.desktop/sun.font",
    "java.desktop/sun.java2d",
    "java.desktop/sun.swing",
    "jdk.attach/sun.tools.attach",
    "jdk.compiler/com.sun.tools.javac.api",
    "jdk.internal.jvmstat/sun.jvmstat.monitor",
    "jdk.jdi/com.sun.tools.jdi",
    "java.desktop/sun.lwawt",
    "java.desktop/sun.lwawt.macosx",
    "java.desktop/com.apple.laf",
    "java.desktop/com.apple.eawt",
    "java.desktop/com.apple.eawt.event",
    "java.management/sun.management",
).map { "--add-opens=$it=ALL-UNNAMED" }

tasks.test {
    javaLauncher = jbrToolchain.javaLauncher
    // The MPS runtime jars come from the unpacked distribution, like the production daemon's classpath, so the
    // IntelliJ platform detects the MPS home from the jar locations and loads bundled plugins and languages from it.
    classpath += files(
        testMpsRoot.map { root ->
            fileTree(root) {
                include("lib/*.jar")
                include("lib/modules/*.jar")
            }
        },
    )
    jvmArgs(mpsAddOpens)
    jvmArgumentProviders.add {
        listOf(
            "-Dtest.mpsHome=${testMpsRoot.get()}",
            "-Dtest.projectsDir=${rootDir.resolve("test-projects")}",
            "-Djava.awt.headless=true",
        )
    }
}

val dist = configurations.consumable("dist") {
    outgoing.artifact(tasks.installDist)
}

// Enforce that nothing on the daemon's runtime classpath carries a non-relocated kotlinx.serialization. On the daemon's
// flat classpath (our jars first, then MPS's), a stray non-relocated copy would shadow MPS's own serialization runtime.
// Our copy must only ever appear under the relocated `com.specificlanguages.mops.shaded.kotlinx.serialization` package.
val checkDaemonRelocation by tasks.registering {
    val runtimeClasspath = configurations.runtimeClasspath
    inputs.files(runtimeClasspath).withNormalizer(ClasspathNormalizer::class)

    doLast {
        val forbiddenPrefix = "kotlinx/serialization/"
        val violations = mutableListOf<String>()

        runtimeClasspath.get().files.filter { it.name.endsWith(".jar") }.forEach { jar ->
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().filter { it.name.endsWith(".class") }.forEach { entry ->
                    // A leaked non-relocated runtime jar would carry class files under the forbidden package.
                    if (entry.name.startsWith(forbiddenPrefix)) {
                        violations += "${jar.name}: ships non-relocated class ${entry.name}"
                        return@forEach
                    }

                    val referenced = sortedSetOf<String>()
                    val remapper = object : org.objectweb.asm.commons.Remapper() {
                        override fun map(internalName: String): String {
                            if (internalName.startsWith(forbiddenPrefix)) {
                                referenced += internalName
                            }
                            return internalName
                        }
                    }
                    try {
                        zip.getInputStream(entry).use { input ->
                            org.objectweb.asm.ClassReader(input).accept(
                                org.objectweb.asm.commons.ClassRemapper(
                                    org.objectweb.asm.ClassWriter(0),
                                    remapper,
                                ),
                                0,
                            )
                        }
                    } catch (e: Exception) {
                        // Skip anything ASM cannot parse (e.g. non-standard or future-version class files).
                    }
                    referenced.forEach { name ->
                        violations += "${jar.name}: ${entry.name} references non-relocated $name"
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Non-relocated kotlinx.serialization found on the daemon runtime classpath:")
                    violations.sorted().forEach { appendLine("  - $it") }
                    appendLine("All kotlinx.serialization usage must be relocated under the shaded package.")
                }
            )
        }
    }
}

tasks.named("check") {
    dependsOn(checkDaemonRelocation)
}
