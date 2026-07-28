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

class ShadedProjectFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void consumerCanEmbedShadedProjectOutputsInAggregateJars() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishDeduplicatingRunner(repo);
        writeAggregateProject(repo);

        BuildResult first = gradleWithConfigurationCache(
                ":platform:assertRuntimeClasspathSelectsShadedProject",
                ":platform:jar",
                ":platform:sourcesJar"
        ).build();
        BuildResult second = gradleWithConfigurationCache(
                ":platform:assertRuntimeClasspathSelectsShadedProject",
                ":platform:jar",
                ":platform:sourcesJar"
        ).build();

        assertConfigurationCacheStored(first);
        assertConfigurationCacheReused(second);
        assertTaskCompleted(first, ":common:shadeCommonRuntime");
        assertTaskCompleted(first, ":common:shadeCommonSources");
        assertTaskCompleted(first, ":platform:assertRuntimeClasspathSelectsShadedProject");
        assertTaskCompleted(first, ":platform:jar");
        assertTaskCompleted(first, ":platform:sourcesJar");

        Path runtimeJar = tempDir.resolve("platform/build/libs/platform-1.0.jar");
        TestFixtures.assertJarContains(runtimeJar, "com/example/common/CommonEntrypoint.class");
        TestFixtures.assertJarContains(
                runtimeJar,
                "com/example/common/modshade/net/mezzdev/deduplicating/runner/DeduplicatingRunner.class"
        );
        TestFixtures.assertJarDoesNotContain(
                runtimeJar,
                "net/mezzdev/deduplicating/runner/DeduplicatingRunner.class"
        );
        TestFixtures.assertClassReferences(
                runtimeJar,
                "com/example/common/CommonEntrypoint.class",
                "com/example/common/modshade/net/mezzdev/deduplicating/runner/DeduplicatingRunner"
        );

        Path sourcesJar = tempDir.resolve("platform/build/libs/platform-1.0-sources.jar");
        TestFixtures.assertJarContains(sourcesJar, "com/example/common/CommonEntrypoint.java");
        TestFixtures.assertJarEntryContains(
                sourcesJar,
                "com/example/common/CommonEntrypoint.java",
                "import com.example.common.modshade.net.mezzdev.deduplicating.runner.DeduplicatingRunner;"
        );
        TestFixtures.assertJarEntryDoesNotContain(
                sourcesJar,
                "com/example/common/CommonEntrypoint.java",
                "import net.mezzdev.deduplicating.runner.DeduplicatingRunner;"
        );
        TestFixtures.assertJarEntryCount(sourcesJar, "META-INF/MANIFEST.MF", 1);
        TestFixtures.assertJarEntryDoesNotContain(
                sourcesJar,
                "META-INF/MANIFEST.MF",
                "Producer-Sources-Manifest"
        );
        TestFixtures.assertJarDoesNotContain(sourcesJar, "MANIFEST.MF");
    }

    @Test
    void shadedProjectRuntimeDependencyFailsClearlyWhenProducerHasNoRuntimeOutput() throws IOException {
        writeProjectWithoutProducerRuntimeOutput();

        BuildResult result = gradle(":platform:help").buildAndFail();

        assertTrue(
                result.getOutput().contains("Project ':common' has no ModShade runtime output registered."),
                result.getOutput()
        );
    }

    @Test
    void shadedProjectSourcesContentsFailsClearlyWhenProducerHasNoSourcesOutput() throws IOException {
        writeProjectWithoutProducerSourcesOutput();

        BuildResult result = gradle(":platform:sourcesJar").buildAndFail();

        assertTrue(
                result.getOutput().contains("Project ':common' has no ModShade sources output registered."),
                result.getOutput()
        );
    }

    private void writeAggregateProject(Path repo) throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "aggregate-mod"
                        include("common", "platform")
                        """,
                StandardCharsets.UTF_8
        );
        writeAggregateProducerProject(repo);
        writeAggregateConsumerProject();
    }

    private void writeAggregateProducerProject(Path repo) throws IOException {
        Files.createDirectories(tempDir.resolve("common"));
        Files.writeString(
                tempDir.resolve("common/build.gradle.kts"),
                """
                        import org.gradle.api.tasks.bundling.AbstractArchiveTask
                        import org.gradle.api.tasks.bundling.Jar

                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        group = "com.example.common"
                        version = "1.0"

                        repositories {
                            maven {
                                url = uri("%s")
                            }
                        }

                        java {
                            withSourcesJar()
                        }

                        dependencies {
                            modShadeImplementation("net.mezzdev:deduplicating-runner:0.1.0")
                        }

                        tasks.jar {
                            archiveClassifier.set("unshaded")
                        }

                        tasks.named<Jar>("sourcesJar") {
                            archiveClassifier.set("sources-unshaded")
                            manifest {
                                attributes(mapOf("Producer-Sources-Manifest" to "should-not-copy"))
                            }
                            from(layout.projectDirectory.file("src/sourceManifest/MANIFEST.MF"))
                        }

                        modShade {
                            shadeJar("shadeCommonRuntime", tasks.named<AbstractArchiveTask>("jar"))
                            shadeSourcesJar("shadeCommonSources", tasks.named<AbstractArchiveTask>("sourcesJar"))
                        }
                        """.formatted(repo.toUri()),
                StandardCharsets.UTF_8
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("common/src/main/java/com/example/common/CommonEntrypoint.java"),
                """
                        package com.example.common;

                        import net.mezzdev.deduplicating.runner.DeduplicatingRunner;

                        public final class CommonEntrypoint {
                            public String run() {
                                return DeduplicatingRunner.run();
                            }
                        }
                        """
        );
        Files.createDirectories(tempDir.resolve("common/src/sourceManifest"));
        Files.writeString(
                tempDir.resolve("common/src/sourceManifest/MANIFEST.MF"),
                "Copied-Root-Manifest: should-not-copy\n",
                StandardCharsets.UTF_8
        );
    }

    private void writeAggregateConsumerProject() throws IOException {
        Files.createDirectories(tempDir.resolve("platform"));
        Files.writeString(
                tempDir.resolve("platform/build.gradle.kts"),
                """
                        import org.gradle.api.DefaultTask
                        import org.gradle.api.file.ConfigurableFileCollection
                        import org.gradle.api.tasks.Classpath
                        import org.gradle.api.tasks.TaskAction
                        import org.gradle.api.tasks.bundling.Jar

                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        group = "com.example.platform"
                        version = "1.0"

                        java {
                            withSourcesJar()
                        }

                        val commonShade = modShade.shadedProject(project(":common"))

                        commonShade.addRuntimeDependencyTo(configurations.runtimeOnly)
                        commonShade.runtimeInto(tasks.named<Jar>("jar"))
                        commonShade.sourcesInto(tasks.named<Jar>("sourcesJar"))

                        abstract class AssertRuntimeClasspathSelectsShadedProject : DefaultTask() {
                            @get:Classpath
                            abstract val runtimeClasspath: ConfigurableFileCollection

                            @TaskAction
                            fun assertClasspath() {
                                val names = runtimeClasspath.files.map { it.name }.toSet()
                                check("common-1.0.jar" in names) {
                                    "expected runtimeClasspath to contain the ModShade runtime artifact, got ${'$'}names"
                                }
                                check("common-1.0-unshaded.jar" !in names) {
                                    "expected runtimeClasspath to avoid the unshaded Java runtime artifact, got ${'$'}names"
                                }
                            }
                        }

                        tasks.register<AssertRuntimeClasspathSelectsShadedProject>("assertRuntimeClasspathSelectsShadedProject") {
                            runtimeClasspath.from(configurations.named("runtimeClasspath"))
                        }
                        """,
                StandardCharsets.UTF_8
        );
    }

    private void writeProjectWithoutProducerRuntimeOutput() throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "missing-runtime-output"
                        include("common", "platform")
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("common"));
        Files.writeString(
                tempDir.resolve("common/build.gradle.kts"),
                """
                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("platform"));
        Files.writeString(
                tempDir.resolve("platform/build.gradle.kts"),
                """
                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        val commonShade = modShade.shadedProject(project(":common"))

                        dependencies {
                            runtimeOnly(commonShade.runtimeDependency())
                        }
                        """,
                StandardCharsets.UTF_8
        );
    }

    private void writeProjectWithoutProducerSourcesOutput() throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "missing-sources-output"
                        include("common", "platform")
                        """,
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("common"));
        Files.writeString(
                tempDir.resolve("common/build.gradle.kts"),
                """
                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        modShade {
                            shadeJar()
                        }
                        """,
                StandardCharsets.UTF_8
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("common/src/main/java/com/example/common/CommonEntrypoint.java"),
                "package com.example.common; public final class CommonEntrypoint {}"
        );
        Files.createDirectories(tempDir.resolve("platform"));
        Files.writeString(
                tempDir.resolve("platform/build.gradle.kts"),
                """
                        import org.gradle.api.tasks.bundling.Jar

                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        java {
                            withSourcesJar()
                        }

                        val commonShade = modShade.shadedProject(project(":common"))

                        tasks.named<Jar>("sourcesJar") {
                            from(commonShade.sourcesContents())
                        }
                        """,
                StandardCharsets.UTF_8
        );
    }

    private Path publishDeduplicatingRunner(Path repo) throws IOException {
        return publishFixtureLibrary(
                repo,
                "net.mezzdev",
                "deduplicating-runner",
                "0.1.0",
                """
                        package net.mezzdev.deduplicating.runner;

                        public final class DeduplicatingRunner {
                            public static String run() {
                                return "deduplicated";
                            }
                        }
                        """,
                List.of(),
                List.of(),
                Map.of()
        );
    }
}
