pluginManagement {
    includeBuild("../../plugin")

    repositories {
        maven("https://maven.neoforged.net/releases")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.gradle.toolchains.foojay-resolver-convention") {
                useModule("org.gradle.toolchains:foojay-resolver:${requested.version}")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.neoforged.net/releases")
        maven("https://libraries.minecraft.net")
        mavenCentral()
    }
}

rootProject.name = "modshade-moddevgradle-integration"

include("Library", "Verifier")
project(":Library").projectDir = file("../Library")
project(":Verifier").projectDir = file("../Verifier")
