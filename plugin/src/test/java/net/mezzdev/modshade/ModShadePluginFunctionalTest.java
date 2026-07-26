package net.mezzdev.modshade;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModShadePluginFunctionalTest {
    private static final String MOD_CLASS = "com/example/mod/ExampleMod.class";
    private static final String ORIGINAL_LIBRARY_CLASS = "net/mezzdev/fixture/library/FixtureLibrary.class";
    private static final String ORIGINAL_LIBRARY_INTERNAL_NAME = "net/mezzdev/fixture/library/FixtureLibrary";
    private static final String DEFAULT_RELOCATED_LIBRARY_CLASS = "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.class";
    private static final String DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME = "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary";
    private static final String DEFAULT_RELOCATED_HELPER_CLASS = "com/example/modshade/net/mezzdev/fixture/helper/FixtureHelper.class";
    private static final String DEFAULT_RELOCATED_TRANSITIVE_CLASS = "com/example/modshade/net/mezzdev/fixture/transitive/FixtureTransitive.class";
    private static final String DEFAULT_RELOCATED_EXTERNAL_TRANSITIVE_CLASS = "com/example/modshade/net/mezzdev/fixture/external/FixtureExternalTransitive.class";

    @TempDir
    Path tempDir;

    @Test
    void applyingJavaPluginDoesNotCreateModShadeJarUntilRequested() throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"explicit-modshade\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("net.mezzdev.modshade")
                }

                tasks.register("assertNoImplicitModShadeJar") {
                    doLast {
                        check(tasks.findByName("modShadeJar") == null)
                    }
                }
                """, StandardCharsets.UTF_8);

        BuildResult result = gradle("assertNoImplicitModShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertNoImplicitModShadeJar")).getOutcome());
    }

    @Test
    void modShadeJarTaskCanBeRegisteredDirectlyWithoutExtensionBlock() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProjectWithImports(repo, """
                import net.mezzdev.modshade.task.ModShadeJar
                import net.mezzdev.modshade.task.ModShadeSourcesJar
                import org.gradle.api.tasks.bundling.AbstractArchiveTask
                """, """
                java {
                    withSourcesJar()
                }

                tasks.jar {
                    archiveClassifier.set("unshaded")
                }

                tasks.named<AbstractArchiveTask>("sourcesJar") {
                    archiveClassifier.set("sources-unshaded")
                }

                tasks.register<ModShadeJar>("modShadeJar") {
                    fromArchive(tasks.named<AbstractArchiveTask>("jar"))
                    archiveClassifier.set("")
                }

                tasks.register<ModShadeSourcesJar>("modShadeSourcesJar") {
                    fromArchive(tasks.named<AbstractArchiveTask>("sourcesJar"))
                    archiveClassifier.set("sources")
                }
                """);

        BuildResult result = gradle("modShadeJar", "modShadeSourcesJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeSourcesJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path sourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarContains(sourcesJar, "com/example/mod/ExampleMod.java");
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void directlyRegisteredModShadeJarIsSeparateAndRelocatesProjectBytecodeAndDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());

        Path plainJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        assertTrue(Files.exists(plainJar), "expected the original jar to remain available");
        TestFixtures.assertJarContains(plainJar, MOD_CLASS);
        TestFixtures.assertJarContains(plainJar, "fabric.mod.json");
        TestFixtures.assertJarDoesNotContain(plainJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(plainJar, ORIGINAL_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(plainJar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);

        Path shadedJar = defaultShadedJar();
        assertTrue(Files.exists(shadedJar), "expected separate ModShade jar artifact");
        TestFixtures.assertJarContains(shadedJar, MOD_CLASS);
        TestFixtures.assertJarContains(shadedJar, "fabric.mod.json");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(shadedJar, ORIGINAL_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(shadedJar, "META-INF/maven/net.mezzdev.fixture/library/pom.properties");
        TestFixtures.assertJarDoesNotContain(shadedJar, "META-INF/TEST.SF");
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);
        TestFixtures.assertClassDoesNotReference(shadedJar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void modShadeJarSupportsConfigurationCacheReload() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeConfigurationCacheProperty();
        writeBasicProject(repo, """
                modShade {
                    shadeJar()
                }
                """);

        BuildResult first = gradleWithConfigurationCache("modShadeJar").build();
        BuildResult second = gradleWithConfigurationCache("modShadeJar").build();

        assertConfigurationCacheStored(first);
        assertConfigurationCacheReused(second);
        assertTaskCompleted(first, ":modShadeJar");
        assertTaskCompleted(second, ":modShadeJar");
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void modShadeJarSupportsConfigurationCacheReloadOnGradle812() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeConfigurationCacheProperty();
        writeBasicProject(repo, """
                modShade {
                    shadeJar()
                }
                """);

        BuildResult first = gradle812WithConfigurationCache("modShadeJar").build();
        BuildResult second = gradle812WithConfigurationCache("modShadeJar").build();

        assertConfigurationCacheStored(first);
        assertConfigurationCacheReused(second);
        assertTaskCompleted(first, ":modShadeJar");
        assertTaskCompleted(second, ":modShadeJar");
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void modShadeSourcesJarSupportsConfigurationCacheReload() throws IOException {
        writeConfigurationCacheProperty();
        writeMultiProjectWithLocalPlainHelperSources();

        BuildResult first = gradleWithConfigurationCache("modShadeSourcesJar").build();
        BuildResult second = gradleWithConfigurationCache("modShadeSourcesJar").build();

        assertConfigurationCacheStored(first);
        assertConfigurationCacheReused(second);
        assertTaskCompleted(first, ":modShadeSourcesJar");
        assertTaskCompleted(second, ":modShadeSourcesJar");
        Path sourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarContains(
                sourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void modShadeSourcesJarSupportsConfigurationCacheReloadOnGradle812() throws IOException {
        writeConfigurationCacheProperty();
        writeMultiProjectWithLocalPlainHelperSources();

        BuildResult first = gradle812WithConfigurationCache("modShadeSourcesJar").build();
        BuildResult second = gradle812WithConfigurationCache("modShadeSourcesJar").build();

        assertConfigurationCacheStored(first);
        assertConfigurationCacheReused(second);
        assertTaskCompleted(first, ":modShadeSourcesJar");
        assertTaskCompleted(second, ":modShadeSourcesJar");
        Path sourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarContains(
                sourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void transitiveDependenciesAreShadedByDefault() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishHelperLibrary(repo);
        publishLibraryWithOptionalHelperUse(repo, false);
        writeBasicProject(repo);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = defaultShadedJar();
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_HELPER_CLASS);
    }

    @Test
    void serviceFilesAreMergedAndRelocated() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishFixtureLibrary(
                repo,
                "library",
                """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """,
                List.of(),
                List.of(),
                Map.of(
                        "META-INF/services/net.mezzdev.fixture.library.FixtureLibrary",
                        "net.mezzdev.fixture.library.FixtureLibrary\n"
                )
        );
        writeBasicProject(repo);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/resources/META-INF/services/net.mezzdev.fixture.library.FixtureLibrary"),
                "com.example.mod.ExampleMod\n"
        );

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = defaultShadedJar();
        String relocatedServiceEntry = "META-INF/services/com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary";
        TestFixtures.assertJarContains(shadedJar, relocatedServiceEntry);
        TestFixtures.assertJarDoesNotContain(shadedJar, "META-INF/services/net.mezzdev.fixture.library.FixtureLibrary");
        TestFixtures.assertJarEntryContains(
                shadedJar,
                relocatedServiceEntry,
                "com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary"
        );
        TestFixtures.assertJarEntryContains(
                shadedJar,
                relocatedServiceEntry,
                "com.example.mod.ExampleMod"
        );
    }

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
    void modShadeComponentPublishesGradleModuleMetadataWithoutShadedDependencies() throws IOException {
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
    }

    @Test
    void modShadeImplementationConfigurationMatchesImplementationClasspathWithoutPublishingDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
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
                    modShadeImplementation("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertImplementationModShadeClasspath : DefaultTask() {
                    @get:Input
                    abstract val modShadeImplementationCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeCompileOnlyCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeRuntimeOnlyCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Input
                    abstract val modShadeClasspathCanBeResolved: org.gradle.api.provider.Property<Boolean>

                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(!modShadeImplementationCanBeResolved.get())
                        check(!modShadeCompileOnlyCanBeResolved.get())
                        check(!modShadeRuntimeOnlyCanBeResolved.get())
                        check(modShadeClasspathCanBeResolved.get())
                        check(compileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.any { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertImplementationModShadeClasspath>("assertImplementationModShadeClasspath") {
                    modShadeImplementationCanBeResolved.set(configurations.named("modShadeImplementation").map { it.isCanBeResolved })
                    modShadeCompileOnlyCanBeResolved.set(configurations.named("modShadeCompileOnly").map { it.isCanBeResolved })
                    modShadeRuntimeOnlyCanBeResolved.set(configurations.named("modShadeRuntimeOnly").map { it.isCanBeResolved })
                    modShadeClasspathCanBeResolved.set(configurations.named("modShadeClasspath").map { it.isCanBeResolved })
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

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
                "assertImplementationModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertImplementationModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void modShadeCompileOnlyConfigurationShadesWithoutRuntimeClasspath() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
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
                    modShadeCompileOnly("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertCompileOnlyModShadeClasspath : DefaultTask() {
                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(compileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.any { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.none { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.none { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertCompileOnlyModShadeClasspath>("assertCompileOnlyModShadeClasspath") {
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

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
                "assertCompileOnlyModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertCompileOnlyModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void modShadeRuntimeOnlyConfigurationShadesWithoutCompileClasspath() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
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
                    modShadeRuntimeOnly("net.mezzdev.fixture:library:1.0")
                }

                abstract class AssertRuntimeOnlyModShadeClasspath : DefaultTask() {
                    @get:Classpath
                    abstract val compileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testCompileClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val runtimeClasspath: ConfigurableFileCollection

                    @get:Classpath
                    abstract val testRuntimeClasspath: ConfigurableFileCollection

                    @TaskAction
                    fun assertClasspath() {
                        check(compileClasspath.files.none { it.name == "library-1.0.jar" })
                        check(testCompileClasspath.files.none { it.name == "library-1.0.jar" })
                        check(runtimeClasspath.files.any { it.name == "library-1.0.jar" })
                        check(testRuntimeClasspath.files.any { it.name == "library-1.0.jar" })
                    }
                }

                tasks.register<AssertRuntimeOnlyModShadeClasspath>("assertRuntimeOnlyModShadeClasspath") {
                    compileClasspath.from(configurations.named("compileClasspath"))
                    testCompileClasspath.from(configurations.named("testCompileClasspath"))
                    runtimeClasspath.from(configurations.named("runtimeClasspath"))
                    testRuntimeClasspath.from(configurations.named("testRuntimeClasspath"))
                }

                modShade {
                    shadeJar()
                }

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
                """.formatted(repo.toUri()), StandardCharsets.UTF_8);
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
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );

        BuildResult result = gradle(
                "assertRuntimeOnlyModShadeClasspath",
                "modShadeJar",
                "publish",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertRuntimeOnlyModShadeClasspath")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":publish")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);

        Path pom = tempDir.resolve("build/published/com/example/simple-mod/1.0/simple-mod-1.0.pom");
        String pomText = Files.readString(pom, StandardCharsets.UTF_8);
        assertFalse(pomText.contains("net.mezzdev.fixture"));
        assertFalse(pomText.contains("library"));
    }

    @Test
    void configuredRelocationBaseControlsInferredRelocationTarget() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    relocationBase.set("com.example.libs")
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        String relocatedClass = "com/example/libs/net/mezzdev/fixture/library/FixtureLibrary.class";
        String relocatedInternalName = "com/example/libs/net/mezzdev/fixture/library/FixtureLibrary";
        TestFixtures.assertJarContains(jar, relocatedClass);
        TestFixtures.assertJarDoesNotContain(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, ORIGINAL_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, relocatedInternalName);
        TestFixtures.assertClassDoesNotReference(jar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void explicitRelocationRuleOverridesInferredRelocation() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    relocate("net.mezzdev.fixture", "com.example.vendor.fixture")
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        String relocatedClass = "com/example/vendor/fixture/library/FixtureLibrary.class";
        String relocatedInternalName = "com/example/vendor/fixture/library/FixtureLibrary";
        TestFixtures.assertJarContains(jar, relocatedClass);
        TestFixtures.assertJarDoesNotContain(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, ORIGINAL_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, relocatedInternalName);
        TestFixtures.assertClassDoesNotReference(jar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void customExcludePatternIsAppliedToShadedDependencyContents() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of("assets/fixture-library/hidden.txt")
        );
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    exclude("assets/fixture-library/**")
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, "assets/fixture-library/hidden.txt");
    }

    @Test
    void groovyDslExcludeAddsToDefaultExcludes() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of("assets/org/hidden.txt", "META-INF/NOTICE.txt")
        );
        writeGroovyBasicProject(repo, """
                modShade {
                    shadeJar()
                    exclude('assets/org/**')
                    exclude('META-INF/*.txt')
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, "META-INF/maven/net.mezzdev.fixture/library/pom.properties");
        TestFixtures.assertJarDoesNotContain(jar, "META-INF/TEST.SF");
        TestFixtures.assertJarDoesNotContain(jar, "assets/org/hidden.txt");
        TestFixtures.assertJarDoesNotContain(jar, "META-INF/NOTICE.txt");
    }

    @Test
    void canOptOutOfModJarGuardForLegacyPlainLibraryArtifacts() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of("quilt.mod.json", "mcmod.info")
        );
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    failOnModJars.set(false)
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, "quilt.mod.json");
        TestFixtures.assertJarDoesNotContain(jar, "mcmod.info");
    }

    @Test
    void explicitRelocationDisablesInferredRelocationRules() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    relocate("com.acme", "com.example.vendor.acme")
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        TestFixtures.assertJarContains(jar, ORIGINAL_LIBRARY_CLASS);
        TestFixtures.assertJarDoesNotContain(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void canRegisterModShadeJarFromCustomJarTask() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                val clientJar by tasks.registering(Jar::class) {
                    archiveClassifier.set("client")
                    from(sourceSets.main.get().output)
                }

                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeClientJar") {
                    fromArchive(clientJar)
                }
                """);

        BuildResult result = gradle("modShadeClientJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":clientJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeClientJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0-client-modshade.jar");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void shadeJarBuildsExplicitNormalNamedRuntimeJar() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                modShade {
                    shadeJar()
                }
                """);

        BuildResult result = gradle("assemble").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());

        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(shadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(shadedJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path unshadedJar = tempDir.resolve("build/libs/simple-mod-1.0-unshaded.jar");
        TestFixtures.assertJarContains(unshadedJar, MOD_CLASS);
        TestFixtures.assertJarDoesNotContain(unshadedJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(unshadedJar, MOD_CLASS, ORIGINAL_LIBRARY_INTERNAL_NAME);
    }

    @Test
    void shadeJarDetectsRemapJarArchiveTask() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                val remapJar by tasks.registering(Jar::class) {
                    from(sourceSets.main.get().output)
                }

                modShade {
                    shadeJar()
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":remapJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path unshadedRemapJar = tempDir.resolve("build/libs/simple-mod-1.0-unshaded.jar");
        TestFixtures.assertJarContains(unshadedRemapJar, MOD_CLASS);
        TestFixtures.assertJarDoesNotContain(unshadedRemapJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
    }

    @Test
    void shadeJarDetectsReobfJarArchiveTask() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                val reobfMarker = layout.buildDirectory.file("reobf-marker/reobf-only.txt")

                val writeReobfMarker by tasks.registering {
                    outputs.file(reobfMarker)
                    doLast {
                        val markerFile = reobfMarker.get().asFile
                        markerFile.parentFile.mkdirs()
                        markerFile.writeText("reobf")
                    }
                }

                val reobfJar by tasks.registering(Jar::class) {
                    dependsOn(writeReobfMarker)
                    from(sourceSets.main.get().output)
                    from(reobfMarker)
                }

                modShade {
                    shadeJar()
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":reobfJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(jar, "reobf-only.txt");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(jar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path unshadedReobfJar = tempDir.resolve("build/libs/simple-mod-1.0-unshaded.jar");
        TestFixtures.assertJarContains(unshadedReobfJar, "reobf-only.txt");
        TestFixtures.assertJarContains(unshadedReobfJar, MOD_CLASS);
        TestFixtures.assertJarDoesNotContain(unshadedReobfJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
    }

    @Test
    void shadeJarDetectsReobfJarWhenSourceJarIsFinalizedInPlace() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register("reobfJar") {
                    dependsOn(tasks.jar)
                    outputs.file(layout.buildDirectory.file("reobf-marker.txt"))
                    doLast {
                        layout.buildDirectory.file("reobf-marker.txt").get().asFile.writeText("done")
                    }
                }

                modShade {
                    shadeJar()
                }

                tasks.named("modShadeJar") {
                    doFirst {
                        check(layout.buildDirectory.file("reobf-marker.txt").get().asFile.isFile)
                    }
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":reobfJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
    }

    @Test
    void sourcesHelperCreatesRelocatedSourcesJarFromLocalProjectDependencySources() throws IOException {
        writeMultiProjectWithLocalPlainHelperSources();

        BuildResult result = gradle(
                "assemble",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":sourcesJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":plain-helper:jar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeSourcesJar")).getOutcome());
        Path sourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarContains(sourcesJar, "com/example/mod/ExampleMod.java");
        TestFixtures.assertJarContains(
                sourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarDoesNotContain(
                sourcesJar,
                "net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
        TestFixtures.assertJarEntryDoesNotContain(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import net.mezzdev.fixture.library.FixtureLibrary;"
        );
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java",
                "package com.example.modshade.net.mezzdev.fixture.library;"
        );
    }

    @Test
    void sourcesHelperDetectsRemapSourcesJarArchiveTask() throws IOException {
        writeMultiProjectWithLocalPlainHelperSources("""
                val remapSourcesJar by tasks.registering(Jar::class) {
                    archiveClassifier.set("sources")
                    from(sourceSets.main.get().allSource)
                }

                modShade {
                    shadeSourcesJar()
                }
                """);

        BuildResult result = gradle("modShadeSourcesJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":remapSourcesJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeSourcesJar")).getOutcome());

        Path sourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources.jar");
        TestFixtures.assertJarContains(sourcesJar, "com/example/mod/ExampleMod.java");
        TestFixtures.assertJarContains(
                sourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarDoesNotContain(
                sourcesJar,
                "net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );

        Path unshadedSourcesJar = tempDir.resolve("build/libs/simple-mod-1.0-sources-unshaded.jar");
        TestFixtures.assertJarContains(unshadedSourcesJar, "com/example/mod/ExampleMod.java");
        TestFixtures.assertJarDoesNotContain(
                unshadedSourcesJar,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java"
        );
        TestFixtures.assertJarEntryContains(
                unshadedSourcesJar,
                "com/example/mod/ExampleMod.java",
                "import net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void helperMethodsSupportCustomTaskNames() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                java {
                    withSourcesJar()
                }

                val remapJar by tasks.registering(Jar::class) {
                    archiveBaseName.set("custom-remap")
                    archiveClassifier.set("unshaded")
                    from(sourceSets.main.get().output)
                }

                val reobfArchive by tasks.registering(Jar::class) {
                    archiveBaseName.set("custom-reobf")
                    archiveClassifier.set("unshaded")
                    from(sourceSets.main.get().output)
                }

                val finalArchive by tasks.registering(Jar::class) {
                    archiveBaseName.set("custom-final")
                    archiveClassifier.set("unshaded")
                    from(sourceSets.main.get().output)
                }

                tasks.named<Jar>("sourcesJar") {
                    archiveBaseName.set("custom-sources")
                    archiveClassifier.set("sources-unshaded")
                }

                modShade {
                    shadeJar("customShadeJar", finalArchive)
                    shadeJar("customShadeRemap", remapJar)
                    shadeJar("customShadeReobf", reobfArchive)
                    shadeSourcesJar("customShadeSources", tasks.named<AbstractArchiveTask>("sourcesJar"))
                }
                """);

        BuildResult result = gradle("customShadeJar", "customShadeRemap", "customShadeReobf", "customShadeSources").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":customShadeJar")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":customShadeRemap")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":customShadeReobf")).getOutcome());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":customShadeSources")).getOutcome());

        Path finalJar = tempDir.resolve("build/libs/custom-final-1.0.jar");
        TestFixtures.assertJarContains(finalJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(finalJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path remapJar = tempDir.resolve("build/libs/custom-remap-1.0.jar");
        TestFixtures.assertJarContains(remapJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(remapJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path reobfJar = tempDir.resolve("build/libs/custom-reobf-1.0.jar");
        TestFixtures.assertJarContains(reobfJar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertClassReferences(reobfJar, MOD_CLASS, DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME);

        Path sourcesJar = tempDir.resolve("build/libs/custom-sources-1.0-sources.jar");
        TestFixtures.assertJarContains(sourcesJar, "com/example/mod/ExampleMod.java");
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/mod/ExampleMod.java",
                "import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"
        );
    }

    @Test
    void doesNotApplyStandaloneShadowPlugin() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register("assertNoStandaloneShadowPlugin") {
                    doLast {
                        check(!plugins.hasPlugin("com.gradleup.shadow"))
                        check(tasks.findByName("shadowJar") == null)
                    }
                }
                """);

        BuildResult result = gradle("assertNoStandaloneShadowPlugin").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":assertNoStandaloneShadowPlugin")).getOutcome());
    }

    @Test
    void modShadeReportWritesConfigurationSummary() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    relocationBase.set("com.example.libs")
                    relocate("net.mezzdev.fixture", "com.example.vendor.fixture")
                    exclude("assets/fixture-library/**")
                }
                """);

        BuildResult result = gradle(
                "modShadeReport",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeReport")).getOutcome());
        Path report = tempDir.resolve("build/reports/modshade/modShadeReport.txt");
        assertTrue(Files.exists(report), "expected ModShade report file");
        String reportText = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(reportText.contains("modShadeJar"));
        assertTrue(reportText.contains("library-1.0.jar"));
        assertTrue(reportText.contains("net.mezzdev.fixture -> com.example.vendor.fixture"));
        assertTrue(reportText.contains("assets/fixture-library/**"));
    }

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

    private void writeBasicProject(Path repo) throws IOException {
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }
                """);
    }

    private void writeBasicProject(Path repo, String modShadeBlock) throws IOException {
        writeBasicProjectWithImports(repo, "", modShadeBlock);
    }

    private void writeBasicProjectWithImports(Path repo, String imports, String buildScriptBody) throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                %s

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
                    modShadeImplementation("net.mezzdev.fixture:library:1.0")
                }

                %s
                """.formatted(imports, repo.toUri(), buildScriptBody), StandardCharsets.UTF_8);
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
    }

    private void writeGroovyBasicProject(Path repo, String buildScriptBody) throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'simple-mod'\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'net.mezzdev.modshade'
                }

                group = 'com.example'
                version = '1.0'

                repositories {
                    maven {
                        url = uri('%s')
                    }
                }

                dependencies {
                    modShadeImplementation 'net.mezzdev.fixture:library:1.0'
                }

                %s
                """.formatted(repo.toUri(), buildScriptBody), StandardCharsets.UTF_8);
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
    }

    private void writeMultiProjectWithLocalPlainHelperSources() throws IOException {
        writeMultiProjectWithLocalPlainHelperSources("""
                modShade {
                    shadeSourcesJar()
                }
                """);
    }

    private void writeMultiProjectWithLocalPlainHelperSources(String buildScriptBody) throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "simple-mod"
                        include("plain-helper")
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

                        repositories {
                            mavenCentral()
                        }

                        java {
                            withSourcesJar()
                        }

                        dependencies {
                            modShadeImplementation(project(":plain-helper"))
                        }

                        %s
                        """.formatted(buildScriptBody),
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("plain-helper"));
        Files.writeString(
                tempDir.resolve("plain-helper/build.gradle.kts"),
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

                        import net.mezzdev.fixture.library.FixtureLibrary;

                        public final class ExampleMod {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("plain-helper/src/main/java/net/mezzdev/fixture/library/FixtureLibrary.java"),
                """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """
        );
    }

    private Path publishHelperLibrary(Path repo) throws IOException {
        return publishHelperLibrary(repo, List.of());
    }

    private Path publishHelperLibrary(Path repo, List<MavenDependency> dependencies) throws IOException {
        return publishFixtureLibrary(
                repo,
                "helper",
                """
                        package net.mezzdev.fixture.helper;

                        public final class FixtureHelper {
                            public static String value() {
                                return "helper";
                            }
                        }
                        """,
                dependencies,
                List.of()
        );
    }

    private Path publishExternalTransitiveLibrary(Path repo) throws IOException {
        return publishFixtureLibrary(
                repo,
                "external-transitive",
                """
                        package net.mezzdev.fixture.external;

                        public final class FixtureExternalTransitive {
                            public static String value() {
                                return "external";
                            }
                        }
                        """,
                List.of(),
                List.of()
        );
    }

    private Path publishLibraryWithOptionalHelperUse(Path repo, boolean useHelper) throws IOException {
        String source = useHelper
                ? """
                        package net.mezzdev.fixture.library;

                        import net.mezzdev.fixture.helper.FixtureHelper;

                        public final class FixtureLibrary {
                            public static String value() {
                                return FixtureHelper.value();
                            }
                        }
                        """
                : """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """;
        Path helperJar = repo.resolve("net/mezzdev/fixture/helper/1.0/helper-1.0.jar");
        List<Path> classpath = useHelper ? List.of(helperJar) : List.of();
        return publishFixtureLibrary(
                repo,
                "library",
                source,
                List.of(new MavenDependency("net.mezzdev.fixture", "helper", "1.0")),
                classpath
        );
    }

    private Path publishFixtureLibrary(
            Path repo,
            String artifact,
            String sourceText,
            List<MavenDependency> dependencies,
            List<Path> classpath
    ) throws IOException {
        return publishFixtureLibrary(repo, artifact, sourceText, dependencies, classpath, Map.of());
    }

    private Path publishFixtureLibrary(
            Path repo,
            String artifact,
            String sourceText,
            List<MavenDependency> dependencies,
            List<Path> classpath,
            Map<String, String> extraTextEntries
    ) throws IOException {
        Path workDir = Files.createTempDirectory(tempDir, "fixture-" + artifact);
        String sourceFileName = switch (artifact) {
            case "helper" -> "FixtureHelper.java";
            case "external-transitive" -> "FixtureExternalTransitive.java";
            default -> "FixtureLibrary.java";
        };
        Path source = workDir.resolve("src").resolve(sourceFileName);
        TestFixtures.writeJavaSource(source, sourceText);

        Path classesDir = workDir.resolve("classes");
        TestFixtures.compileJavaSources(classesDir, List.of(source), classpath);

        String group = "net.mezzdev.fixture";
        String version = "1.0";
        Path moduleDir = repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        Files.createDirectories(moduleDir);

        List<String> classEntries;
        try (var paths = Files.walk(classesDir)) {
            classEntries = paths
                    .filter(Files::isRegularFile)
                    .map(classesDir::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        Map<String, String> textEntries = new LinkedHashMap<>();
        textEntries.put("META-INF/maven/" + group + "/" + artifact + "/pom.properties", "version=" + version);
        textEntries.putAll(extraTextEntries);
        Path jar = moduleDir.resolve(artifact + "-" + version + ".jar");
        TestFixtures.createJar(jar, classesDir, classEntries, textEntries);

        Files.writeString(
                moduleDir.resolve(artifact + "-" + version + ".pom"),
                """
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                            %s
                        </project>
                        """.formatted(group, artifact, version, formatDependencies(dependencies)),
                StandardCharsets.UTF_8
        );
        return jar;
    }

    private static String formatDependencies(List<MavenDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return "";
        }

        StringBuilder dependencyText = new StringBuilder();
        dependencyText.append("<dependencies>\n");
        for (MavenDependency dependency : dependencies) {
            dependencyText.append("""
                                    <dependency>
                                        <groupId>%s</groupId>
                                        <artifactId>%s</artifactId>
                                        <version>%s</version>
                                    </dependency>
                    """.formatted(dependency.group(), dependency.artifact(), dependency.version()));
        }
        dependencyText.append("                            </dependencies>");
        return dependencyText.toString();
    }

    private record MavenDependency(String group, String artifact, String version) {
    }

    private Path defaultShadedJar() {
        return tempDir.resolve("build/libs/simple-mod-1.0-modshade.jar");
    }

    private void writeConfigurationCacheProperty() throws IOException {
        Files.writeString(tempDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\n", StandardCharsets.UTF_8);
    }

    private GradleRunner gradleWithConfigurationCache(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--configuration-cache");
        arguments.add("--configuration-cache-problems=fail");
        return gradle(arguments.toArray(String[]::new));
    }

    private GradleRunner gradle812WithConfigurationCache(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--configuration-cache");
        arguments.add("--configuration-cache-problems=fail");
        return gradle(arguments.toArray(String[]::new))
                .withGradleVersion("8.12.1");
    }

    private static void assertTaskCompleted(BuildResult result, String taskPath) {
        TaskOutcome outcome = Objects.requireNonNull(result.task(taskPath)).getOutcome();
        assertTrue(
                outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
                () -> taskPath + " should complete, but was " + outcome
        );
    }

    private static void assertConfigurationCacheStored(BuildResult result) {
        assertTrue(
                result.getOutput().contains("Configuration cache entry stored."),
                "expected Gradle to store a configuration cache entry"
        );
    }

    private static void assertConfigurationCacheReused(BuildResult result) {
        assertTrue(
                result.getOutput().contains("Reusing configuration cache."),
                "expected Gradle to reuse the configuration cache entry"
        );
    }

    private GradleRunner gradle(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

}
