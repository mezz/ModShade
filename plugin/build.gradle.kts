import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    signing
}

group = "net.mezzdev.gradle"
version = "0.1.0"
description = "Gradle plugin for safely shading implementation-only libraries into Minecraft mod jars."

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")

    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")

    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("modShade") {
            id = "net.mezzdev.modshade"
            implementationClass = "net.mezzdev.modshade.ModShadePlugin"
            displayName = "ModShade"
            description = project.description
        }
    }
}

val signingKey = providers.gradleProperty("signingKey")
val signingPassword = providers.gradleProperty("signingPassword")

fun isPublishingToCentral(): Boolean =
    gradle.startParameter.taskNames.any { taskName ->
        taskName == "publish" || taskName.contains("Sonatype")
    }

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("ModShade")
            description.set(project.description)
            url.set("https://github.com/mezz/ModShade")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit/")
                }
            }
            developers {
                developer {
                    id.set("mezz")
                    name.set("mezz")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/mezz/ModShade.git")
                developerConnection.set("scm:git:ssh://git@github.com/mezz/ModShade.git")
                url.set("https://github.com/mezz/ModShade")
            }
        }
    }

    repositories {
        maven {
            name = "dryRun"
            url = layout.buildDirectory.dir("repos/dry-run").get().asFile.toURI()
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

signing {
    val inMemorySigningKey = signingKey.orNull
    if (!inMemorySigningKey.isNullOrBlank()) {
        useInMemoryPgpKeys(inMemorySigningKey, signingPassword.orNull)
    }
    isRequired = isPublishingToCentral()
    sign(publishing.publications)
}

tasks.register("publishDryRun") {
    group = "publishing"
    description = "Publishes all Maven publications to a local dry-run repository under build/repos/dry-run."
    dependsOn("publishAllPublicationsToDryRunRepository")
}
