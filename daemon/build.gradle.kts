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

    mpsRuntime(zipTree({ mpsZip.singleFile }).matching {
        include("lib/mps-core.jar")
        include("lib/mps-collections.jar")
        include("lib/mps-closures.jar")
        include("lib/mps-environment.jar")
        include("lib/mps-persistence.jar")
        include("lib/mps-platform.jar")
        include("lib/mps-openapi.jar")
        include("lib/mps-references.jar")
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
