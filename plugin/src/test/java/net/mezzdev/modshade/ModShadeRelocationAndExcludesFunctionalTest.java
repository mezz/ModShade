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

class ModShadeRelocationAndExcludesFunctionalTest extends ModShadeFunctionalTestSupport {
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
    void excludesSetReplacesDefaultExcludes() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(
                repo,
                "net.mezzdev.fixture",
                "library",
                "1.0",
                List.of("assets/fixture-library/hidden.txt", "META-INF/NOTICE.txt")
        );
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }

                modShade {
                    exclude("META-INF/NOTICE.txt")
                    excludes.set(listOf("assets/fixture-library/**"))
                }
                """);

        BuildResult result = gradle("modShadeJar").build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path jar = defaultShadedJar();
        TestFixtures.assertJarContains(jar, DEFAULT_RELOCATED_LIBRARY_CLASS);
        TestFixtures.assertJarContains(jar, "META-INF/maven/net.mezzdev.fixture/library/pom.properties");
        TestFixtures.assertJarContains(jar, "META-INF/TEST.SF");
        TestFixtures.assertJarContains(jar, "META-INF/NOTICE.txt");
        TestFixtures.assertJarDoesNotContain(jar, "assets/fixture-library/hidden.txt");
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
}
