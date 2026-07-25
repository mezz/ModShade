import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.12"
    id("net.mezzdev.modshade")
}

val modJavaVersion = providers.gradleProperty("modJavaVersion").map(String::toInt)
val fabricMinecraftVersion = providers.gradleProperty("fabricMinecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()

group = "com.example.modshade.fabric"
version = "1.0.0"

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-fabric-loom-non-remap")
}

java {
    toolchain {
        languageVersion.set(modJavaVersion.map(JavaLanguageVersion::of))
    }
    withSourcesJar()
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(modJavaVersion.map(JavaLanguageVersion::of))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(modJavaVersion)
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(modJavaVersion)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$fabricMinecraftVersion")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    modShade(project(":Library"))
}

loom {
    mods {
        create("modshade_fabric_loom_non_remap_integration") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveClassifier.set("sources-unshaded")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Fabric Loom 1.17 non-remap public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/fabric/api/**")
    }
}

modShade {
    shadeJar()
    shadeSourcesJar()
}

tasks.assemble {
    dependsOn(apiJar)
}

val verifierSourceSets = project(":Verifier").extensions.getByType<SourceSetContainer>()
val verifierRuntimeClasspath = verifierSourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map { it.runtimeClasspath }

tasks.register<JavaExec>("verifyIntegration") {
    group = "verification"
    description = "Builds and verifies the Fabric Loom 1.17 non-remap integration artifacts."
    dependsOn("assemble", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-non-remap-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-non-remap-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-fabric-loom-non-remap-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-fabric-loom-non-remap-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-fabric-loom-non-remap-1.0.0-api.jar"),
        "--loader-metadata", "fabric.mod.json",
        "--mod-class", "com/example/modshade/integration/fabric/FabricIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/fabric/FabricIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/fabric/api/FabricIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.fabric.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "net/minecraft/world/item/Items",
        "--required-runtime-reference", "getDescriptionId",
        "--required-source-text", "net.minecraft.world.item.Items",
        "--required-source-text", "getDescriptionId()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath
