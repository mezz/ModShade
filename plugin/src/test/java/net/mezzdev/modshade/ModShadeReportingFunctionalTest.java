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

class ModShadeReportingFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void modShadeReportWritesConfigurationSummary() throws IOException {
        Path repo = tempDir.resolve("repo");
        String keptResource = "assets/fixture-library/kept.txt";
        String excludedResource = "assets/fixture-library/hidden-one.txt";
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of(keptResource, excludedResource, "META-INF/NOTICE.txt")
        );
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    relocationBase.set("com.example.libs")
                    relocate("net.mezzdev.fixture", "com.example.vendor.fixture")
                    exclude("assets/fixture-library/hidden-*.txt")
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
        assertTrue(reportText.contains("""
                Configuration:
                 - Mod jar shading: disallowed
                 - Relocation base: com.example.libs
                """));
        assertTrue(reportText.contains("modShadeJar"));
        assertTrue(reportText.contains("library-1.0.jar"));
        assertTrue(reportText.contains("net.mezzdev.fixture -> com.example.vendor.fixture"));
        assertTrue(reportText.contains("assets/fixture-library/hidden-*.txt"));
        assertTrue(reportText.contains("Resources included from library-1.0.jar"));
        assertTrue(reportText.contains("META-INF/NOTICE.txt"));
        assertTrue(reportText.contains(keptResource));
        assertFalse(reportText.contains(excludedResource));
        assertFalse(reportText.contains("META-INF/maven/net.mezzdev.fixture/library/pom.properties"));
        assertFalse(reportText.contains("META-INF/TEST.SF"));
    }

    @Test
    void modShadeReportListsNoneWhenNoDependencyResourcesAreIncluded() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }
                """);

        BuildResult result = gradle(
                "modShadeReport",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeReport")).getOutcome());
        String reportText = Files.readString(tempDir.resolve("build/reports/modshade/modShadeReport.txt"), StandardCharsets.UTF_8);
        assertTrue(reportText.contains("""
                Dependency resources included:
                 - <none>
                """));
        assertFalse(reportText.contains("Resources included from library-1.0.jar"));
    }
}
