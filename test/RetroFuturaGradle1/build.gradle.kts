import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    `maven-publish`
    id("com.gtnewhorizons.retrofuturagradle") version "1.4.9"
    id("net.mezzdev.modshade")
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val mappingsVersion = providers.gradleProperty("mappingsVersion").get()
val modJavaVersion = providers.gradleProperty("modJavaVersion").map(String::toInt)
val buildJavaVersion = 17
val modJavaCompatibility = modJavaVersion.map(JavaVersion::toVersion)

group = "com.example.modshade.forge"
version = "1.0.0"

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-forge-112")
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
    modShadeImplementation(project(":Library"))
}

minecraft {
    mcVersion.set(minecraftVersion)
    mcpMappingChannel.set("stable")
    mcpMappingVersion.set(mappingsVersion)
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Forge 1.12.2 public API jar without shaded implementation libraries."
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
    description = "Builds and verifies the Forge 1.12.2 integration artifacts."
    dependsOn("assemble", "publishMavenJavaPublicationToIntegrationRepository", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-forge-112-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-forge-112-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-forge-112-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-forge-112-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-forge-112-1.0.0-api.jar"),
        "--published-runtime-jar", publishedArtifact("modshade-integration-forge-112-1.0.0.jar"),
        "--published-sources-jar", publishedArtifact("modshade-integration-forge-112-1.0.0-sources.jar"),
        "--published-api-jar", publishedArtifact("modshade-integration-forge-112-1.0.0-api.jar"),
        "--published-pom", publishedArtifact("modshade-integration-forge-112-1.0.0.pom"),
        "--published-module", publishedArtifact("modshade-integration-forge-112-1.0.0.module"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--loader-metadata", "mcmod.info",
        "--mod-class", "com/example/modshade/integration/forge/ForgeIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/forge/ForgeIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/forge/api/ForgeIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.forge.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "func_77658_a",
        "--forbidden-runtime-reference", "getTranslationKey",
        "--required-source-text", "net.minecraft.init.Items",
        "--required-source-text", "getTranslationKey()",
        "--required-module-variant", "modShadeRuntimeElements",
        "--required-module-variant", "modShadeSourcesElements",
        "--forbidden-module-variant", "runtimeElements",
        "--forbidden-module-variant", "sourcesElements",
        "--required-module-artifact-file", "modshade-integration-forge-112-1.0.0.jar",
        "--required-module-artifact-file", "modshade-integration-forge-112-1.0.0-sources.jar",
        "--forbidden-pom-text", "modshade-integration-library",
        "--forbidden-module-text", "modshade-integration-library",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath

fun publishedArtifact(fileName: String): String =
    layout.buildDirectory.file("published/${group.toString().replace('.', '/')}/${base.archivesName.get()}/$version/$fileName")
        .get()
        .asFile
        .absolutePath
