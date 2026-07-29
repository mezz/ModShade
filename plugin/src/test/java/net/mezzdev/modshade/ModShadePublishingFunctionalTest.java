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

class ModShadePublishingFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void modShadeDependenciesAreNotPublishedAsMavenRuntimeOrApiDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo);

        String buildScript = Files.readString(tempDir.resolve("build.gradle.kts"), StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), buildScript + """

                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """, StandardCharsets.UTF_8);

        BuildResult result = gradle("publish").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void shadeHelperReturnValuesWirePublishingAndConsumerTasksWithoutTaskNames() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                java {
                    withSourcesJar()
                }

                val shadedJar = modShade.shadeJar()
                val shadedSourcesJar = modShade.shadeSourcesJar()

                abstract class ConsumeStableModShadeOutputs : DefaultTask() {
                    @get:InputFile
                    abstract val runtimeJar: RegularFileProperty

                    @get:InputFile
                    abstract val sourcesJar: RegularFileProperty

                    @get:OutputFile
                    abstract val outputFile: RegularFileProperty

                    @TaskAction
                    fun consume() {
                        val runtimeJarFile = runtimeJar.get().asFile
                        val sourcesJarFile = sourcesJar.get().asFile
                        check(runtimeJarFile.name == "simple-mod-1.0.jar")
                        check(sourcesJarFile.name == "simple-mod-1.0-sources.jar")
                        outputFile.get().asFile.apply {
                            parentFile.mkdirs()
                            writeText(runtimeJarFile.name + "\\n" + sourcesJarFile.name)
                        }
                    }
                }

                val consumedRuntimeProvider = layout.buildDirectory.file("provider-check/runtime.txt")

                tasks.register<ConsumeStableModShadeOutputs>("consumeStableModShadeOutputs") {
                    runtimeJar.set(shadedJar.flatMap { it.archiveFile })
                    sourcesJar.set(shadedSourcesJar.flatMap { it.archiveFile })
                    outputFile.set(consumedRuntimeProvider)
                }

                publishing {
                    publications {
                        create<MavenPublication>("mavenJava") {
                            artifact(shadedJar)
                            artifact(shadedSourcesJar)
                        }
                    }
                    repositories {
                        maven {
                            name = "test"
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """);

        BuildResult result = gradle(
                "consumeStableModShadeOutputs",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeSourcesJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":consumeStableModShadeOutputs")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());

        Path providerCheck = tempDir.resolve("build/provider-check/runtime.txt");
        assertEquals(
                "simple-mod-1.0.jar\nsimple-mod-1.0-sources.jar",
                Files.readString(providerCheck, StandardCharsets.UTF_8)
        );

        Path publishedRuntimeJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(publishedRuntimeJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(publishedRuntimeJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path publishedSourcesJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarEntryContains(
                publishedSourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void legacyModShadeComponentStillPublishesShadedRuntimeVariant() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("shadow") {
                            from(components["modShade"])
                        }
                    }
                    repositories {
                        maven {
                            name = "test"
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """);

        BuildResult result = gradle(
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":generateMetadataFileForShadowPublication")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());

        Path publishedRuntimeJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(publishedRuntimeJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(publishedRuntimeJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertNoMavenDependency(pomText, "library");

        Path moduleMetadata = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.module");
        String moduleMetadataText = Files.readString(moduleMetadata, StandardCharsets.UTF_8);
        assertTrue(moduleMetadataText.contains("\"name\": \"modShadeRuntimeElements\""));
        assertNoGradleModuleDependency(moduleMetadataText, "library");
    }

    @Test
    void javaComponentPublishesGradleModuleMetadataWithShadedRuntimeVariantWithoutShadedDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                java {
                    withSourcesJar()
                }

                modShade {
                    shadeJar()
                    shadeSourcesJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("shadow") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            name = "test"
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """);

        BuildResult result = gradle(
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":generateMetadataFileForShadowPublication")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());

        Path publishedRuntimeJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(publishedRuntimeJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(publishedRuntimeJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path publishedSourcesJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarEntryContains(
                publishedSourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));

        Path moduleMetadata = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.module");
        assertTrue(Files.exists(moduleMetadata), "expected Gradle module metadata");
        String moduleMetadataText = Files.readString(moduleMetadata, StandardCharsets.UTF_8);
        assertFalse(moduleMetadataText.contains("net.mezzdev.fixture"));
        assertTrue(moduleMetadataText.contains("\"name\": \"modShadeRuntimeElements\""));
        assertFalse(moduleMetadataText.contains("\"name\": \"runtimeElements\""));
        assertTrue(moduleMetadataText.contains("\"name\": \"modShadeSourcesElements\""));
        assertFalse(moduleMetadataText.contains("\"name\": \"sourcesElements\""));
    }

    @Test
    void javaComponentPublishesNormalJavaRuntimeDependenciesWithoutBundledDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        publishFixtureLibrary(
                repo,
                "api-library",
                """
                        package net.mezzdev.fixture.api;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "api";
                            }
                        }
                        """,
                List.of(),
                List.of()
        );
        publishHelperLibrary(repo);
        publishExternalTransitiveLibrary(repo);

        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    `java-library`
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    api("net.mezzdev.fixture:api-library:1.0")
                    implementation("net.mezzdev.fixture:helper:1.0")
                    runtimeOnly("net.mezzdev.fixture:external-transitive:1.0")
                    modShadeImplementation("net.mezzdev.fixture:library:1.0")
                }

                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("shadow") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            name = "test"
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        import net.mezzdev.fixture.library.FixtureLibrary;

                        public final class ExampleMod {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":generateMetadataFileForShadowPublication")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());

        Path publishedRuntimeJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(publishedRuntimeJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(publishedRuntimeJar, "net/mezzdev/fixture/api/FixtureLibrary.class");
        TestFixtures.assertJarDoesNotContain(publishedRuntimeJar, DEFAULT_RELOCATED_HELPER_CLASS);
        TestFixtures.assertJarDoesNotContain(publishedRuntimeJar, DEFAULT_RELOCATED_EXTERNAL_TRANSITIVE_CLASS);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertMavenDependency(pomText, "api-library");
        assertMavenDependency(pomText, "helper");
        assertMavenDependency(pomText, "external-transitive");
        assertNoMavenDependency(pomText, "library");

        Path moduleMetadata = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.module");
        String moduleMetadataText = Files.readString(moduleMetadata, StandardCharsets.UTF_8);
        assertTrue(moduleMetadataText.contains("\"name\": \"modShadeRuntimeElements\""));
        assertFalse(moduleMetadataText.contains("\"name\": \"runtimeElements\""));
        assertGradleModuleDependency(moduleMetadataText, "api-library");
        assertGradleModuleDependency(moduleMetadataText, "helper");
        assertGradleModuleDependency(moduleMetadataText, "external-transitive");
        assertNoGradleModuleDependency(moduleMetadataText, "library");
    }

    @Test
    void javaComponentPublishesTransitiveDependencyExcludedFromShadingWhenDeclaredNormally() throws IOException {
        Path repo = tempDir.resolve("repo");
        Path externalHelperJar = publishFixtureLibrary(
                repo,
                "org.external.fixture",
                "external-helper",
                "1.0",
                """
                        package org.external.helper;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "helper";
                            }
                        }
                        """,
                List.of(),
                List.of(),
                Map.of()
        );
        publishFixtureLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return org.external.helper.FixtureLibrary.value();
                            }
                        }
                        """,
                List.of(new MavenDependency("org.external.fixture", "external-helper", "1.0")),
                List.of(externalHelperJar),
                Map.of()
        );

        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    implementation("org.external.fixture:external-helper:1.0")
                    modShadeImplementation("net.mezzdev.fixture:library:1.0") {
                        exclude(group = "org.external.fixture", module = "external-helper")
                    }
                }

                modShade {
                    shadeJar()
                }

                publishing {
                    publications {
                        create<MavenPublication>("shadow") {
                            from(components["java"])
                        }
                    }
                    repositories {
                        maven {
                            name = "test"
                            url = layout.buildDirectory.dir("published").get().asFile.toURI()
                        }
                    }
                }
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
                """
                        package com.example.mod;

                        import net.mezzdev.fixture.library.FixtureLibrary;

                        public final class ExampleMod {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":generateMetadataFileForShadowPublication")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());

        Path publishedRuntimeJar = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(publishedRuntimeJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(publishedRuntimeJar, "org/external/helper/FixtureLibrary.class");
        TestFixtures.assertClassReferences(
                publishedRuntimeJar,
                DEFAULT_RELOCATED_LIBRARY_CLASS,
                "org/external/helper/FixtureLibrary"
        );

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertMavenDependency(pomText, "org.external.fixture", "external-helper");
        assertNoMavenDependency(pomText, "library");

        Path moduleMetadata = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.module");
        String moduleMetadataText = Files.readString(moduleMetadata, StandardCharsets.UTF_8);
        assertTrue(moduleMetadataText.contains("\"name\": \"modShadeRuntimeElements\""));
        assertFalse(moduleMetadataText.contains("\"name\": \"runtimeElements\""));
        assertGradleModuleDependency(moduleMetadataText, "org.external.fixture", "external-helper");
        assertNoGradleModuleDependency(moduleMetadataText, "library");
    }

    private static void assertMavenDependency(String pomText, String artifact) {
        assertMavenDependency(pomText, "net.mezzdev.fixture", artifact);
    }

    private static void assertMavenDependency(String pomText, String group, String artifact) {
        assertTrue(
                pomText.contains("<groupId>" + group + "</groupId>")
                        && pomText.contains("<artifactId>" + artifact + "</artifactId>")
                        && pomText.contains("<version>1.0</version>"),
                () -> "expected Maven dependency " + group + ":" + artifact + ":1.0"
        );
    }

    private static void assertNoMavenDependency(String pomText, String artifact) {
        assertFalse(
                pomText.contains("<artifactId>" + artifact + "</artifactId>"),
                () -> "did not expect Maven dependency net.mezzdev.fixture:" + artifact + ":1.0"
        );
    }

    private static void assertGradleModuleDependency(String moduleMetadataText, String artifact) {
        assertGradleModuleDependency(moduleMetadataText, "net.mezzdev.fixture", artifact);
    }

    private static void assertGradleModuleDependency(String moduleMetadataText, String group, String artifact) {
        assertTrue(
                moduleMetadataText.contains("\"group\": \"" + group + "\"")
                        && moduleMetadataText.contains("\"module\": \"" + artifact + "\"")
                        && moduleMetadataText.contains("\"requires\": \"1.0\""),
                () -> "expected Gradle module dependency " + group + ":" + artifact + ":1.0"
        );
    }

    private static void assertNoGradleModuleDependency(String moduleMetadataText, String artifact) {
        assertFalse(
                moduleMetadataText.contains("\"module\": \"" + artifact + "\""),
                () -> "did not expect Gradle module dependency net.mezzdev.fixture:" + artifact + ":1.0"
        );
    }
}
