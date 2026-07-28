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

class ModShadePluginApplicationFunctionalTest extends ModShadeFunctionalTestSupport {
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
}
