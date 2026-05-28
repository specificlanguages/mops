plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://artifacts.itemis.cloud/repository/maven-mps")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
