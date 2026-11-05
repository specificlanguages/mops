plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://artifacts.itemis.cloud/repository/maven-mps")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

// The git revision in mops-version.properties changes with every commit. Ignoring it means a new commit alone does not
// rerun the tests.
normalization.runtimeClasspath {
    properties("mops-version.properties") { ignoreProperty("revision") }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
