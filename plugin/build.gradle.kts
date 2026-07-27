import org.gradle.api.publish.maven.MavenPublication
import java.util.concurrent.Callable

plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    signing
}

group = "net.mezzdev.gradle"
version = "0.3.0"
description = "Gradle plugin for safely shading implementation-only libraries into Minecraft mod jars."

dependencies {
    compileOnly("org.jspecify:jspecify:1.0.0")

    // Shadow 8.3.x keeps ModShade's minimum Gradle version at 8.3. Upgrading
    // to Shadow 9.x raises that floor; update ModShadePlugin.MINIMUM_GRADLE_VERSION
    // and the README compatibility docs together.
    implementation("com.gradleup.shadow:shadow-gradle-plugin:8.3.11")
    implementation("org.apache.ant:ant:1.10.15")

    testCompileOnly("org.jspecify:jspecify:1.0.0")
    testImplementation("org.ow2.asm:asm:9.10.1")
    testImplementation("org.ow2.asm:asm-commons:9.10.1")
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
    } else {
        useGpgCmd()
    }
    setRequired(Callable {
        gradle.taskGraph.allTasks.any { task ->
            task.name.contains("Sonatype") || task.name.endsWith("ToSonatypeRepository")
        }
    })
    sign(publishing.publications)
}

tasks.register("publishDryRun") {
    group = "publishing"
    description = "Publishes all Maven publications to a local dry-run repository under build/repos/dry-run."
    dependsOn("publishAllPublicationsToDryRunRepository")
}
