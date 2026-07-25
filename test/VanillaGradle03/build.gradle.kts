import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    `java-library`
    id("org.spongepowered.gradle.vanilla") version "0.3.2"
    id("net.mezzdev.modshade")
}

val modJavaVersion = providers.gradleProperty("modJavaVersion").map(String::toInt)
val vanillaMinecraftVersion = providers.gradleProperty("vanillaMinecraftVersion").get()

group = "com.example.modshade.vanillagradle"
version = "1.0.0"

repositories {
    maven("https://libraries.minecraft.net")
    mavenCentral()
}

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-vanillagradle03")
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

minecraft {
    version(vanillaMinecraftVersion)
}

dependencies {
    modShade(project(":Library"))
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

tasks.named<AbstractArchiveTask>("sourcesJar") {
    archiveClassifier.set("sources-unshaded")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the VanillaGradle public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/vanilla/api/**")
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
    description = "Builds and verifies the VanillaGradle integration artifacts."
    dependsOn("assemble", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-vanillagradle03-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-vanillagradle03-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-vanillagradle03-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-vanillagradle03-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-vanillagradle03-1.0.0-api.jar"),
        "--mod-class", "com/example/modshade/integration/vanilla/VanillaGradleIntegrationModule.class",
        "--mod-source", "com/example/modshade/integration/vanilla/VanillaGradleIntegrationModule.java",
        "--api-class", "com/example/modshade/integration/vanilla/api/VanillaGradleIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/vanillagradle/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/vanillagradle/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.vanillagradle.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "net/minecraft/SharedConstants",
        "--required-runtime-reference", "getCurrentVersion",
        "--required-source-text", "net.minecraft.SharedConstants",
        "--required-source-text", "getCurrentVersion()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath
