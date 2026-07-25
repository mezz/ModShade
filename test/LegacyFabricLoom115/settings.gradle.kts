pluginManagement {
    includeBuild("../../plugin")

    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Legacy Fabric"
            url = uri("https://maven.legacyfabric.net/")
        }
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
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Legacy Fabric"
            url = uri("https://maven.legacyfabric.net/")
        }
        maven("https://libraries.minecraft.net")
        mavenCentral()
    }
}

rootProject.name = "modshade-legacy-fabric-loom-115-integration"

include("Library", "Verifier")
project(":Library").projectDir = file("../Library")
project(":Verifier").projectDir = file("../Verifier")
