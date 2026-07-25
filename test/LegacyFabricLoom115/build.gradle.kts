import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    id("net.fabricmc.fabric-loom-remap") version "1.15.3"
    id("legacy-looming") version "1.15.3"
    id("net.mezzdev.modshade")
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val yarnBuild = providers.gradleProperty("yarnBuild").get()
val loaderVersion = providers.gradleProperty("loaderVersion").get()
val modJavaVersion = providers.gradleProperty("modJavaVersion").map(String::toInt)
val buildJavaVersion = 17
val modJavaCompatibility = modJavaVersion.map(JavaVersion::toVersion)

group = "com.example.modshade.legacyfabric"
version = "1.0.0"

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-legacy-fabric-loom-115")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(buildJavaVersion))
    }
    sourceCompatibility = modJavaCompatibility.get()
    targetCompatibility = modJavaCompatibility.get()
    withSourcesJar()
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(buildJavaVersion))
            }
            if (project.name != "Verifier") {
                sourceCompatibility = modJavaCompatibility.get()
                targetCompatibility = modJavaCompatibility.get()
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            if (project.name == "Verifier") {
                options.release.set(buildJavaVersion)
            } else {
                sourceCompatibility = modJavaCompatibility.get().toString()
                targetCompatibility = modJavaCompatibility.get().toString()
            }
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    sourceCompatibility = modJavaCompatibility.get().toString()
    targetCompatibility = modJavaCompatibility.get().toString()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")
    add("mappings", "net.legacyfabric:yarn:$minecraftVersion+build.$yarnBuild:v2")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")

    modShade(project(":Library"))
}

loom {
    mods {
        create("modshade_legacy_fabric_loom_115_integration") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.jar {
    archiveClassifier.set("dev")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Legacy Fabric public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/legacyfabric/api/**")
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
    description = "Builds and verifies the Legacy Fabric Loom 1.15 integration artifacts."
    dependsOn("assemble", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-legacy-fabric-loom-115-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-legacy-fabric-loom-115-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-legacy-fabric-loom-115-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-legacy-fabric-loom-115-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-legacy-fabric-loom-115-1.0.0-api.jar"),
        "--loader-metadata", "fabric.mod.json",
        "--mod-class", "com/example/modshade/integration/legacyfabric/LegacyFabricIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/legacyfabric/LegacyFabricIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/legacyfabric/api/LegacyFabricIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/legacyfabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/legacyfabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.legacyfabric.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "net/minecraft/class_1734",
        "--required-runtime-reference", "method_3342",
        "--forbidden-runtime-reference", "net/minecraft/item/Items",
        "--forbidden-runtime-reference", "getTranslationKey",
        "--required-source-text", "net.minecraft.class_1734",
        "--required-source-text", "method_3342()",
        "--forbidden-source-text", "net.minecraft.item.Items",
        "--forbidden-source-text", "getTranslationKey()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath
