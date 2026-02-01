plugins {
    kotlin("jvm") version "2.2.21"
    application
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "21"
    modules = listOf(
        "javafx.controls",
        "javafx.graphics",
        "javafx.media"
    )
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}