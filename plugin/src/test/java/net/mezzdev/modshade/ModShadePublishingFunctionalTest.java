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
}
