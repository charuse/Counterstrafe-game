// build.gradle.kts
plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.valorant"
version = "1.0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls",
        "javafx.graphics",
        "javafx.media"
    )
}

application {
    mainClass.set("LauncherKt")
}

tasks.shadowJar {
    manifest {
        attributes["Main-Class"] = "LauncherKt"
    }
    archiveClassifier.set("all")
}