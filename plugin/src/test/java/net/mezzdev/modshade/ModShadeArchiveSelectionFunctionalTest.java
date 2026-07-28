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

class ModShadeArchiveSelectionFunctionalTest extends ModShadeFunctionalTestSupport {
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
}
