import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    java
    id("net.minecraftforge.gradle") version "5.1.77"
    id("net.mezzdev.modshade")
}

val forgeMinecraftVersion = providers.gradleProperty("forgeMinecraftVersion").get()
val forgeVersion = providers.gradleProperty("forgeVersion").get()
val forgeJavaVersion = providers.gradleProperty("forgeJavaVersion").map(String::toInt)
val buildJavaVersion = 17
val forgeJavaCompatibility = JavaVersion.toVersion(forgeJavaVersion.get())

group = "com.example.modshade.forge"
version = "1.0.0"

project(":Library") {
    group = "net.mezzdev.modshade.integration"
    version = rootProject.version
}
evaluationDependsOn(":Verifier")

base {
    archivesName.set("modshade-integration-forge-legacy")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(buildJavaVersion))
    }
    sourceCompatibility = forgeJavaCompatibility
    targetCompatibility = forgeJavaCompatibility
    withSourcesJar()
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension>("java") {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(buildJavaVersion))
            }
            if (project.name != "Verifier") {
                sourceCompatibility = forgeJavaCompatibility
                targetCompatibility = forgeJavaCompatibility
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            if (project.name == "Verifier") {
                options.release.set(buildJavaVersion)
            } else {
                sourceCompatibility = forgeJavaCompatibility.toString()
                targetCompatibility = forgeJavaCompatibility.toString()
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
    sourceCompatibility = forgeJavaCompatibility.toString()
    targetCompatibility = forgeJavaCompatibility.toString()
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

    modShade(project(":Library"))
}

minecraft {
    mappings("official", forgeMinecraftVersion)

    runs {
        create("client") {
            taskName("runClientDev")
            property("forge.logging.console.level", "debug")
            workingDirectory(file("run/client"))
            mods {
                create("modshade_forge_legacy_integration") {
                    source(sourceSets.main.get())
                }
            }
        }
        create("server") {
            taskName("runServerDev")
            property("forge.logging.console.level", "debug")
            workingDirectory(file("run/server"))
            mods {
                create("modshade_forge_legacy_integration") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

tasks.jar {
    archiveClassifier.set("unshaded")
}

val apiJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds the Forge legacy public API jar without shaded implementation libraries."
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

sourceSets.forEach {
    val outputDir = layout.buildDirectory.dir("sourceSets/${it.name}")
    it.output.resourcesDir = outputDir.get().asFile
    it.java.destinationDirectory.set(outputDir)
}

val verifierSourceSets = project(":Verifier").extensions.getByType<SourceSetContainer>()
val verifierRuntimeClasspath = verifierSourceSets.named(SourceSet.MAIN_SOURCE_SET_NAME).map { it.runtimeClasspath }

tasks.register<JavaExec>("verifyIntegration") {
    group = "verification"
    description = "Builds and verifies the Forge legacy integration artifacts."
    dependsOn("assemble", project(":Verifier").tasks.named("classes"))
    classpath(verifierRuntimeClasspath)
    mainClass.set("net.mezzdev.modshade.integration.VerifyModShadeArtifacts")
    args(
        "--diagnostic-jar", artifact("modshade-integration-forge-legacy-1.0.0-unshaded.jar"),
        "--diagnostic-jar", artifact("modshade-integration-forge-legacy-1.0.0-sources-unshaded.jar"),
        "--runtime-jar", artifact("modshade-integration-forge-legacy-1.0.0.jar"),
        "--sources-jar", artifact("modshade-integration-forge-legacy-1.0.0-sources.jar"),
        "--api-jar", artifact("modshade-integration-forge-legacy-1.0.0-api.jar"),
        "--loader-metadata", "META-INF/mods.toml",
        "--mod-class", "com/example/modshade/integration/forge/ForgeIntegrationMod.class",
        "--mod-source", "com/example/modshade/integration/forge/ForgeIntegrationMod.java",
        "--api-class", "com/example/modshade/integration/forge/api/ForgeIntegrationApi.class",
        "--relocated-library-class", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary.class",
        "--relocated-library-internal-name", "com/example/modshade/forge/modshade/net/mezzdev/modshade/fixture/FixtureLibrary",
        "--relocated-package", "com.example.modshade.forge.modshade.net.mezzdev.modshade.fixture",
        "--required-runtime-reference", "func_77658_a",
        "--forbidden-runtime-reference", "getDescriptionId",
        "--required-source-text", "net.minecraft.item.Items",
        "--required-source-text", "getDescriptionId()",
    )
}

fun artifact(fileName: String): String =
    layout.buildDirectory.file("libs/$fileName").get().asFile.absolutePath
