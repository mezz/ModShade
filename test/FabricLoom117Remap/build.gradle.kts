import net.fabricmc.loom.task.RemapJarTask
import net.fabricmc.loom.task.RemapSourcesJarTask
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    `maven-publish`
    id("net.fabricmc.fabric-loom-remap") version "1.17.12"
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
project(":NestedJarLibrary") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-fabric-loom-remap")
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
    add("mappings", "net.fabricmc:yarn:$fabricMinecraftVersion+build.10:v2")
    implementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")

    modShadeImplementation(project(":Library"))
    add("include", project(":NestedJarLibrary"))
}

loom {
    mods {
        create("modshade_fabric_loom_remap_integration") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.jar {
    archiveClassifier.set("dev")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Fabric Loom 1.17 remap public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/fabric/api/**")
    }
}

val apiIntermediaryJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Fabric Loom 1.17 API/intermediary input jar before Loom remapping."
    archiveClassifier.set("api-intermediary-dev")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/fabric/api/**")
    }
}

val apiIntermediarySourcesJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Fabric Loom 1.17 API/intermediary input sources jar before Loom remapping."
    archiveClassifier.set("api-intermediary-sources-dev")
    from(sourceSets.main.get().allSource) {
        include("com/example/modshade/integration/fabric/api/**")
    }
}

val remapApiIntermediaryJar by tasks.registering(RemapJarTask::class) {
    group = "build"
    description = "Remaps the Fabric Loom 1.17 API/intermediary jar before ModShade relocates implementation libraries."
    inputFile.set(apiIntermediaryJar.flatMap { it.archiveFile })
    archiveClassifier.set("api-intermediary-unshaded")
}

val remapApiIntermediarySourcesJar by tasks.registering(RemapSourcesJarTask::class) {
    group = "build"
    description = "Remaps the Fabric Loom 1.17 API/intermediary sources jar before ModShade relocates implementation libraries."
    inputFile.set(apiIntermediarySourcesJar.flatMap { it.archiveFile })
    archiveClassifier.set("api-intermediary-sources-unshaded")
}

modShade {
    shadeJar()
    shadeSourcesJar()
    shadeJar("modShadeApiIntermediaryJar", remapApiIntermediaryJar).configure {
        archiveClassifier.set("api-intermediary")
    }
    shadeSourcesJar("modShadeApiIntermediarySourcesJar", remapApiIntermediarySourcesJar).configure {
        archiveClassifier.set("api-intermediary-sources")
    }
}

tasks.assemble {
    dependsOn(apiJar)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = base.archivesName.get()
            from(components["java"])
            artifact(apiJar)
        }
    }
    repositories {
        maven {
            name = "integration"
            url = layout.buildDirectory.dir("published").get().asFile.toURI()
        }
    }
}

val verifierSourceSets = project(":Verifier").extensions.getByType<SourceSetContainer>()
val verifierRuntimeClasspath = verifierSourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map { it.runtimeClasspath }

tasks.register<JavaExec>("verifyIntegration") {
    group = "verification"
    description = "Builds and verifies the Fabric Loom 1.17 remap integration artifacts."
    dependsOn(
        "assemble",
        "publishMavenJavaPublicationToIntegrationRepository",
        project(":Verifier").tasks.named("classes"),
        "verifyIntermediaryApiIntegration",
    )
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api.jar"),
        "--published-runtime-jar", publishedArtifact("modshade-integration-fabric-loom-remap-1.0.0.jar"),
        "--published-sources-jar", publishedArtifact("modshade-integration-fabric-loom-remap-1.0.0-sources.jar"),
        "--published-api-jar", publishedArtifact("modshade-integration-fabric-loom-remap-1.0.0-api.jar"),
        "--published-pom", publishedArtifact("modshade-integration-fabric-loom-remap-1.0.0.pom"),
        "--published-module", publishedArtifact("modshade-integration-fabric-loom-remap-1.0.0.module"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--loader-metadata", "fabric.mod.json",
        "--mod-class", "com/example/modshade/integration/fabric/FabricIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/fabric/FabricIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/fabric/api/FabricIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.fabric.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-entry-prefix-and-suffix", "META-INF/jars/::nested-jar-library-1.0.0.jar",
        "--required-runtime-reference", "net/minecraft/class_1802",
        "--required-runtime-reference", "method_7876",
        "--forbidden-runtime-reference", "net/minecraft/item/Items",
        "--forbidden-runtime-reference", "getTranslationKey",
        "--required-source-text", "net.minecraft.class_1802",
        "--required-source-text", "method_7876()",
        "--forbidden-source-text", "net.minecraft.item.Items",
        "--forbidden-source-text", "getTranslationKey()",
        "--required-module-variant", "modShadeRuntimeElements",
        "--required-module-variant", "modShadeSourcesElements",
        "--forbidden-module-variant", "runtimeElements",
        "--forbidden-module-variant", "sourcesElements",
        "--required-module-artifact-file", "modshade-integration-fabric-loom-remap-1.0.0.jar",
        "--required-module-artifact-file", "modshade-integration-fabric-loom-remap-1.0.0-sources.jar",
        "--forbidden-pom-text", "modshade-integration-library",
        "--forbidden-module-text", "modshade-integration-library",
        "--forbidden-pom-text", "nested-jar-library",
        "--forbidden-module-text", "nested-jar-library",
    )
}

tasks.register<JavaExec>("verifyIntermediaryApiIntegration") {
    group = "verification"
    description = "Builds and verifies the optional Fabric API/intermediary ModShade artifacts."
    dependsOn("assemble", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api-intermediary-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api-intermediary-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api-intermediary.jar"),
        "--sources-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api-intermediary-sources.jar"),
        "--api-jar", artifact("modshade-integration-fabric-loom-remap-1.0.0-api.jar"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--mod-class", "com/example/modshade/integration/fabric/api/FabricIntegrationApi.class",
        "--mod-source", "com/example/modshade/integration/fabric/api/FabricIntegrationApi.java",
        "--api-class", "com/example/modshade/integration/fabric/api/FabricIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/fabric/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.fabric.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "net/minecraft/class_1802",
        "--required-runtime-reference", "method_7876",
        "--forbidden-runtime-reference", "net/minecraft/item/Items",
        "--forbidden-runtime-reference", "getTranslationKey",
        "--required-source-text", "net.minecraft.class_1802",
        "--required-source-text", "method_7876()",
        "--forbidden-source-text", "net.minecraft.item.Items",
        "--forbidden-source-text", "getTranslationKey()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath

fun publishedArtifact(fileName: String): String =
    layout.buildDirectory.file("published/${group.toString().replace('.', '/')}/${base.archivesName.get()}/$version/$fileName")
        .get()
        .asFile
        .absolutePath
