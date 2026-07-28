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

class ModShadeConfigurationCacheFunctionalTest extends ModShadeFunctionalTestSupport {
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
}
