plugins {
    id("mops.kotlin-jvm-conventions")
    `java-library`
}

dependencies {
    api(project(":protocol"))
}
