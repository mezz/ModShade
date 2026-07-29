import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.publish.maven.MavenPublication

plugins {
    java
    `maven-publish`
    id("net.neoforged.gradle.userdev") version "7.1.38"
    id("net.mezzdev.modshade")
}

val modJavaVersion = providers.gradleProperty("modJavaVersion").map(String::toInt)
val neoforgeVersion = providers.gradleProperty("neoforgeVersion").get()

group = "com.example.modshade.neoforge"
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
    archivesName.set("modshade-integration-neogradle")
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
    implementation("net.neoforged:neoforge:$neoforgeVersion")

    modShadeImplementation(project(":Library"))
    add("jarJar", project(":NestedJarLibrary"))
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the NeoGradle public API jar without shaded implementation libraries."
    archiveClassifier.set("api")
    from(sourceSets.main.get().output) {
        include("com/example/modshade/integration/neoforge/api/**")
    }
}

modShade {
    shadeJar()
    shadeSourcesJar()
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
    description = "Builds and verifies the NeoGradle integration artifacts."
    dependsOn("assemble", "publishMavenJavaPublicationToIntegrationRepository", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-neogradle-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-neogradle-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-neogradle-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-neogradle-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-neogradle-1.0.0-api.jar"),
        "--published-runtime-jar", publishedArtifact("modshade-integration-neogradle-1.0.0.jar"),
        "--published-sources-jar", publishedArtifact("modshade-integration-neogradle-1.0.0-sources.jar"),
        "--published-api-jar", publishedArtifact("modshade-integration-neogradle-1.0.0-api.jar"),
        "--published-pom", publishedArtifact("modshade-integration-neogradle-1.0.0.pom"),
        "--published-module", publishedArtifact("modshade-integration-neogradle-1.0.0.module"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--loader-metadata", "META-INF/neoforge.mods.toml",
        "--mod-class", "com/example/modshade/integration/neoforge/NeoGradleIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/neoforge/NeoGradleIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/neoforge/api/NeoForgeIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/neoforge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/neoforge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.neoforge.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-entry", "META-INF/jarjar/metadata.json",
        "--required-runtime-entry-prefix-and-suffix", "META-INF/jarjar/::nested-jar-library-1.0.0.jar",
        "--required-runtime-reference", "net/minecraft/world/item/Items",
        "--required-runtime-reference", "getDescriptionId",
        "--required-source-text", "net.minecraft.world.item.Items",
        "--required-source-text", "getDescriptionId()",
        "--required-module-variant", "modShadeRuntimeElements",
        "--required-module-variant", "modShadeSourcesElements",
        "--forbidden-module-variant", "runtimeElements",
        "--forbidden-module-variant", "sourcesElements",
        "--required-module-artifact-file", "modshade-integration-neogradle-1.0.0.jar",
        "--required-module-artifact-file", "modshade-integration-neogradle-1.0.0-sources.jar",
        "--forbidden-pom-text", "modshade-integration-library",
        "--forbidden-module-text", "modshade-integration-library",
        "--forbidden-pom-text", "nested-jar-library",
        "--forbidden-module-text", "nested-jar-library",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath

fun publishedArtifact(fileName: String): String =
    layout.buildDirectory.file("published/${group.toString().replace('.', '/')}/${base.archivesName.get()}/$version/$fileName")
        .get()
        .asFile
        .absolutePath
