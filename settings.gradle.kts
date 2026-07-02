pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "mops"

include("cli")
include("daemon")
include("daemon-core")
include("launcher")
include("protocol")
