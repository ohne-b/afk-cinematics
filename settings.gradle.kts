pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases/")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.7.11"
}

rootProject.name = "afk-cinematics"
include("core")

stonecutter {
    create(rootProject) {
        val releases = listOf(
            "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
            "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
            "26.1", "26.1.1", "26.1.2", "26.2", "26.3"
        )
        for (minecraft in releases) {
            for (loader in listOf("fabric", "neoforge")) {
                version("$minecraft-$loader", minecraft).buildscript =
                    if (loader == "fabric" && minecraft.startsWith("26."))
                        "build.fabric-modern.gradle.kts"
                    else "build.$loader.gradle.kts"
            }
        }
        vcsVersion = "1.21.11-fabric"
    }
}
