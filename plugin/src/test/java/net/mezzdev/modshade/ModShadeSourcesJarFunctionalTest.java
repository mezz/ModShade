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

class ModShadeSourcesJarFunctionalTest extends ModShadeFunctionalTestSupport {
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
}
