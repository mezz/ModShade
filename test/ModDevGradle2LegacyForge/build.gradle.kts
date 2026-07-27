import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    id("net.neoforged.moddev.legacyforge") version "2.0.142"
    id("net.mezzdev.modshade")
}

val legacyForgeVersion = providers.gradleProperty("legacyForgeVersion").get()
val forgeJavaVersion = providers.gradleProperty("forgeJavaVersion").map(String::toInt)

group = "com.example.modshade.forge"
version = "1.0.0"

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-moddevgradle-legacyforge")
}

java {
    toolchain {
        languageVersion.set(forgeJavaVersion.map(JavaLanguageVersion::of))
    }
    withSourcesJar()
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(forgeJavaVersion.map(JavaLanguageVersion::of))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(forgeJavaVersion)
        }

        tasks.withType<AbstractArchiveTask>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(forgeJavaVersion)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    modShadeImplementation(project(":Library"))
}

legacyForge {
    version = legacyForgeVersion
    validateAccessTransformers = true
    runs {
        create("client") {
            client()
            gameDirectory = file("run/client")
        }
        create("server") {
            server()
            gameDirectory = file("run/server")
        }
    }

    mods {
        create("modshade_moddevgradle_legacyforge_integration") {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the ModDevGradle 2 legacyForge public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/forge/api/**")
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
val additionalRuntimeClasspath = configurations.named("additionalRuntimeClasspath")

tasks.register<JavaExec>("verifyAdditionalRuntimeClasspath") {
    group = "verification"
    description = "Verifies ModShade dependencies are available on ModDevGradle legacyForge additionalRuntimeClasspath."
    dependsOn(project(":Library").tasks.named("jar"), project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(additionalRuntimeClasspath)
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
    )
}

tasks.register<JavaExec>("verifyIntegration") {
    group = "verification"
    description = "Builds and verifies the ModDevGradle 2 legacyForge integration artifacts."
    dependsOn("assemble", "verifyAdditionalRuntimeClasspath", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-moddevgradle-legacyforge-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-moddevgradle-legacyforge-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-moddevgradle-legacyforge-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-moddevgradle-legacyforge-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-moddevgradle-legacyforge-1.0.0-api.jar"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--loader-metadata", "META-INF/mods.toml",
        "--mod-class", "com/example/modshade/integration/forge/ForgeIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/forge/ForgeIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/forge/api/ForgeIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.forge.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "m_5524_",
        "--forbidden-runtime-reference", "getDescriptionId",
        "--required-source-text", "net.minecraft.world.item.Items",
        "--required-source-text", "getDescriptionId()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath
