plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "net.mezzdev.gradle"
version = "0.1.0-SNAPSHOT"
description = "Gradle plugin for safely shading implementation-only libraries into Minecraft mod jars."

dependencies {
    implementation("org.ow2.asm:asm:9.10.1")
    implementation("org.ow2.asm:asm-commons:9.10.1")

    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
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
