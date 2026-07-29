package net.mezzdev.modshade;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModShadeDependencyValidationFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void failsWhenModShadeDependencyIsAFabricModJar() throws IOException {
        Path libs = tempDir.resolve("libs");
        Files.createDirectories(libs);
        TestFixtures.createJar(libs.resolve("fabric-dependency.jar"), List.of("fabric.mod.json"));

        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"bad-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("net.mezzdev.modshade")
                }

                dependencies {
                    modShadeImplementation(files("libs/fabric-dependency.jar"))
                }

                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }
                """, StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                "package com.example.mod; public final class ExampleMod {}"
        );

        BuildResult result = gradle("modShadeJar").buildAndFail();

        assertEquals(TaskOutcome.FAILED, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertFalse(Files.exists(tempDir.resolve("build/libs/bad-mod-modshade.jar")));
    }

    @Test
    void failsWhenModMetadataWouldBeExcludedFromShadedDependencyContents() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of("quilt.mod.json", "assets/fixture-library/hidden.txt")
        );
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    exclude("quilt.mod.json")
                    exclude("assets/fixture-library/**")
                }
                """);

        BuildResult result = gradle("modShadeJar").buildAndFail();

        assertEquals(TaskOutcome.FAILED, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertTrue(result.getOutput().contains("ModShade is for shading plain implementation-only libraries into Minecraft mods"));
        assertTrue(result.getOutput().contains("library-1.0.jar"));
        assertTrue(result.getOutput().contains("quilt.mod.json"));
        assertFalse(Files.exists(defaultShadedJar()));
    }
}
