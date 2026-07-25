pluginManagement {
    includeBuild("../../plugin")

    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "GTNH Maven"
                    setUrl("https://nexus.gtnewhorizons.com/repository/public/")
                }
            }
            filter {
                includeGroup("com.gtnewhorizons")
                includeGroup("com.gtnewhorizons.retrofuturagradle")
            }
        }
        maven("https://maven.minecraftforge.net")
        maven("https://libraries.minecraft.net")
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
            name = "GTNH Maven"
            setUrl("https://nexus.gtnewhorizons.com/repository/public/")
        }
        maven("https://maven.minecraftforge.net")
        maven("https://libraries.minecraft.net")
        mavenCentral()
    }
}

rootProject.name = "modshade-retrofuturagradle-integration"

include("Library", "Verifier")
project(":Library").projectDir = file("../Library")
project(":Verifier").projectDir = file("../Verifier")
