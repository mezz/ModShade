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

class ModShadeRuntimeJarFunctionalTest extends ModShadeFunctionalTestSupport {
    @Test
    void directlyRegisteredModShadeJarIsSeparateAndRelocatesProjectBytecodeAndDependencies() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

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
    void transitiveDependenciesAreShadedByDefault() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishHelperLibrary(repo);
        publishLibraryWithOptionalHelperUse(repo, false);
        writeBasicProject(repo);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

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
    void modShadeJarPreservesSourceManifestAttributesWithoutMinimize() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.jar {
                    manifest {
                        attributes(mapOf(
                            "Implementation-Title" to "SimpleMod",
                            "Automatic-Module-Name" to "simple.mod"
                        ))
                    }
                }

                modShade {
                    shadeJar()
                }
                """);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarEntryCount(shadedJar, "META-INF/MANIFEST.MF", 1);
        TestFixtures.assertManifestAttribute(shadedJar, "Implementation-Title", "SimpleMod");
        TestFixtures.assertManifestAttribute(shadedJar, "Automatic-Module-Name", "simple.mod");
    }

    @Test
    void modShadeJarPreservesSourceManifestAttributesWithMinimize() throws IOException {
        Path repo = tempDir.resolve("repo");
        publishHelperLibrary(repo);
        publishLibraryWithOptionalHelperUse(repo, true);
        writeBasicProject(repo, """
                tasks.jar {
                    manifest {
                        attributes(mapOf(
                            "Implementation-Title" to "SimpleMod",
                            "Automatic-Module-Name" to "simple.mod"
                        ))
                    }
                }

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
        TestFixtures.assertJarEntryCount(shadedJar, "META-INF/MANIFEST.MF", 1);
        TestFixtures.assertManifestAttribute(shadedJar, "Implementation-Title", "SimpleMod");
        TestFixtures.assertManifestAttribute(shadedJar, "Automatic-Module-Name", "simple.mod");
    }

    @Test
    void modShadeJarPreservesSourceManifestAttributesWhenDuplicateStrategyFails() throws IOException {
        Path repo = tempDir.resolve("repo");
        TestFixtures.publishLibrary(repo, "net.mezzdev.fixture", "library", "1.0");
        writeBasicProject(repo, """
                tasks.jar {
                    manifest {
                        attributes(mapOf(
                            "Implementation-Title" to "SimpleMod",
                            "Automatic-Module-Name" to "simple.mod"
                        ))
                    }
                }

                val shadedJar = modShade.shadeJar()
                shadedJar.configure {
                    duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.FAIL
                }
                """);

        BuildResult result = gradle(
                "modShadeJar",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
        ).build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":modShadeJar")).getOutcome());
        Path shadedJar = tempDir.resolve("build/libs/simple-mod-1.0.jar");
        TestFixtures.assertJarEntryCount(shadedJar, "META-INF/MANIFEST.MF", 1);
        TestFixtures.assertManifestAttribute(shadedJar, "Implementation-Title", "SimpleMod");
        TestFixtures.assertManifestAttribute(shadedJar, "Automatic-Module-Name", "simple.mod");
    }
}
