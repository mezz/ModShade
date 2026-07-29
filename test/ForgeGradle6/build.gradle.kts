import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact
import net.minecraftforge.gradle.common.tasks.JarExec
import net.minecraftforge.gradle.userdev.jarjar.JarJarProjectExtension
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    `maven-publish`
    id("net.minecraftforge.gradle") version "6.0.54"
    id("net.mezzdev.modshade")
}

val forgeMinecraftVersion = providers.gradleProperty("forgeMinecraftVersion").get()
val forgeVersion = providers.gradleProperty("forgeVersion").get()
val forgeJavaVersion = providers.gradleProperty("forgeJavaVersion").map(String::toInt)

group = "com.example.modshade.forge"
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
    archivesName.set("modshade-integration-forge")
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
    "minecraft"(
        group = "net.minecraftforge",
        name = "forge",
        version = "$forgeMinecraftVersion-$forgeVersion",
    )

    modShadeImplementation(project(":Library"))
}

val nestedJarJarDependency = dependencies.add("jarJar", project(":NestedJarLibrary")) as ModuleDependency
extensions.getByType<JarJarProjectExtension>().ranged(nestedJarJarDependency, "[1.0.0,)")

minecraft {
    mappings("official", forgeMinecraftVersion)
    copyIdeResources.set(true)

    runs {
        create("client") {
            taskName("runClientDev")
            property("forge.logging.console.level", "debug")
            workingDirectory(file("run/client"))
            mods {
                create("modshade_forge_integration") {
                    source(sourceSets.main.get())
                }
            }
        }
        create("server") {
            taskName("runServerDev")
            property("forge.logging.console.level", "debug")
            workingDirectory(file("run/server"))
            mods {
                create("modshade_forge_integration") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Forge public API jar without shaded implementation libraries."
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

sourceSets.forEach {
    val outputDir = layout.buildDirectory.dir("sourceSets/${it.name}")
    it.output.resourcesDir = outputDir.get().asFile
    it.java.destinationDirectory.set(outputDir)
}

tasks.withType<DownloadMavenArtifact>().configureEach {
    notCompatibleWithConfigurationCache("ForgeGradle resolves artifacts through Task.project at execution time.")
}

tasks.withType<JarExec>().configureEach {
    notCompatibleWithConfigurationCache("ForgeGradle runs external tools through Task.project at execution time.")
}

val verifierSourceSets = project(":Verifier").extensions.getByType<SourceSetContainer>()
val verifierRuntimeClasspath = verifierSourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map { it.runtimeClasspath }

tasks.register<JavaExec>("verifyIntegration") {
    group = "verification"
    description = "Builds and verifies the Forge integration artifacts."
    dependsOn("assemble", "publishMavenJavaPublicationToIntegrationRepository", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    classpath(configurations.named("runtimeClasspath"))
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-forge-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-forge-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-forge-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-forge-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-forge-1.0.0-api.jar"),
        "--published-runtime-jar", publishedArtifact("modshade-integration-forge-1.0.0.jar"),
        "--published-sources-jar", publishedArtifact("modshade-integration-forge-1.0.0-sources.jar"),
        "--published-api-jar", publishedArtifact("modshade-integration-forge-1.0.0-api.jar"),
        "--published-pom", publishedArtifact("modshade-integration-forge-1.0.0.pom"),
        "--published-module", publishedArtifact("modshade-integration-forge-1.0.0.module"),
        "--development-runtime-classpath-entry-prefix", "modshade-integration-library",
        "--loader-metadata", "META-INF/mods.toml",
        "--mod-class", "com/example/modshade/integration/forge/ForgeIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/forge/ForgeIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/forge/api/ForgeIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.forge.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-entry", "META-INF/jarjar/metadata.json",
        "--required-runtime-entry-prefix-and-suffix", "META-INF/jarjar/::nested-jar-library-1.0.0.jar",
        "--required-runtime-reference", "m_5524_",
        "--forbidden-runtime-reference", "getDescriptionId",
        "--required-source-text", "net.minecraft.world.item.Items",
        "--required-source-text", "getDescriptionId()",
        "--required-module-variant", "modShadeRuntimeElements",
        "--required-module-variant", "modShadeSourcesElements",
        "--forbidden-module-variant", "runtimeElements",
        "--forbidden-module-variant", "sourcesElements",
        "--required-module-artifact-file", "modshade-integration-forge-1.0.0.jar",
        "--required-module-artifact-file", "modshade-integration-forge-1.0.0-sources.jar",
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
