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

class ModShadeMinimizeFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void minimizeRemovesUnusedTransitiveDependencyClasses() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishHelperLibrary(repo);
        publishLibraryWithOptionalHelperUse(repo, false);
        writeBasicProject(repo, """
                val shadedJar = modShade.shadeJar()
                shadedJar.configure {
                    minimize()
                }
                """);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(shadedJar, DEFAULT_RELOCATED_HELPER_CLASS);
    }

    @Test
    void minimizeKeepsTransitivelyUsedDependencyClasses() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishHelperLibrary(repo);
        publishLibraryWithOptionalHelperUse(repo, true);
        writeBasicProject(repo, """
                val shadedJar = modShade.shadeJar()
                shadedJar.configure {
                    minimize()
                }
                """);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_HELPER_CLASS);
    }

    @Test
    void minimizeCanForceIncludeDependencyPattern() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishExternalTransitiveLibrary(repo);
        publishHelperLibrary(repo, List.of(new MavenDependency("net.mezzdev.fixture", "external-transitive", "1.0")));
        publishLibraryWithOptionalHelperUse(repo, false);
        writeBasicProject(repo, """
                val shadedJar = modShade.shadeJar()
                shadedJar.configure {
                    minimize {
                        exclude(dependency("net.mezzdev.fixture:helper:.*"))
                    }
                }
                """);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_HELPER_CLASS);
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_EXTERNAL_TRANSITIVE_CLASS);
    }

    @Test
    void minimizeCanForceIncludeProjectDependency() throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "simple-mod"
                        include("plain-helper")
                        include("helper-transitive")
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("build.gradle.kts"),
                """
                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        group = "com.example"
                        version = "1.0"

                        dependencies {
                            modShadeRuntimeOnly(project(":plain-helper"))
                        }

                        val shadedJar = modShade.shadeJar()
                        shadedJar.configure {
                            minimize {
                                exclude(project(":plain-helper"))
                            }
                        }
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("plain-helper"));
        Files.writeString(
                tempDir.resolve("plain-helper/build.gradle.kts"),
                """
                        plugins {
                            java
                        }

                        dependencies {
                            implementation(project(":helper-transitive"))
                        }
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("helper-transitive"));
        Files.writeString(
                tempDir.resolve("helper-transitive/build.gradle.kts"),
                """
                        plugins {
                            java
                        }
                        """,
                StandardCharsets.UTF_8
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        public final class ExampleMod {
                            public String value() {
                                return "fixture";
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("plain-helper/src/main/java/net/mezzdev/fixture/helper/FixtureHelper.java"),
                """
                        package net.mezzdev.fixture.helper;

                        public final class FixtureHelper {
                            public static String value() {
                                return "helper";
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("helper-transitive/src/main/java/net/mezzdev/fixture/transitive/FixtureTransitive.java"),
                """
                        package net.mezzdev.fixture.transitive;

                        public final class FixtureTransitive {
                            public static String value() {
                                return "transitive";
                            }
                        }
                        """
        );

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":plain-helper:jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":helper-transitive:jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_HELPER_CLASS);
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_TRANSITIVE_CLASS);
    }
}
