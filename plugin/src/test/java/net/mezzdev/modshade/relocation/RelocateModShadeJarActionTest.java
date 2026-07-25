package net.mezzdev.modshade.relocation;

import net.mezzdev.modshade.RelocationRule;
import net.mezzdev.modshade.TestFixtures;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RelocateModShadeJarActionTest {
    @TempDir
    Path tempDir;

    @Test
    void relocatesBytecodeEntriesServicesResourcesAndMultiReleaseEntries() throws IOException {
        Path classesDir = compileRelocationFixtureClasses();
        Path versionedClass = classesDir.resolve("META-INF/versions/9/net/mezzdev/fixture/library/Versioned.class");
        Files.createDirectories(versionedClass.getParent());
        Files.copy(classesDir.resolve("net/mezzdev/fixture/library/Versioned.class"), versionedClass);

        Path dependencyJar = tempDir.resolve("dependency.jar");
        TestFixtures.createJar(
                dependencyJar,
                classesDir,
                List.of("net/mezzdev/fixture/library/FixtureLibrary.class"),
                Map.of()
        );

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        Jar archiveTask = createArchiveTask(project);
        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();
        TestFixtures.createJar(
                archive,
                classesDir,
                List.of(
                        "com/example/mod/ExampleMod.class",
                        "net/mezzdev/fixture/library/FixtureLibrary.class",
                        "net/mezzdev/fixture/library/FixtureService.class",
                        "net/mezzdev/fixture/library/impl/Provider.class",
                        "META-INF/versions/9/net/mezzdev/fixture/library/Versioned.class"
                ),
                Map.of(
                        "net/mezzdev/fixture/library/resource.txt", "resource",
                        "META-INF/services/net.mezzdev.fixture.library.FixtureService",
                        "net.mezzdev.fixture.library.impl.Provider\n"
                )
        );

        relocationAction(project, List.of(dependencyJar), List.of())
                .execute(archiveTask);

        TestFixtures.assertJarContains(
                archive,
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.class"
        );
        TestFixtures.assertJarContains(
                archive,
                "com/example/modshade/net/mezzdev/fixture/library/resource.txt"
        );
        TestFixtures.assertJarContains(
                archive,
                "META-INF/services/com.example.modshade.net.mezzdev.fixture.library.FixtureService"
        );
        TestFixtures.assertJarEntryContains(
                archive,
                "META-INF/services/com.example.modshade.net.mezzdev.fixture.library.FixtureService",
                "com.example.modshade.net.mezzdev.fixture.library.impl.Provider"
        );
        TestFixtures.assertJarContains(
                archive,
                "META-INF/versions/9/com/example/modshade/net/mezzdev/fixture/library/Versioned.class"
        );
        TestFixtures.assertJarDoesNotContain(
                archive,
                "net/mezzdev/fixture/library/FixtureLibrary.class"
        );
        TestFixtures.assertJarDoesNotContain(
                archive,
                "META-INF/services/net.mezzdev.fixture.library.FixtureService"
        );
        TestFixtures.assertClassReferences(
                archive,
                "com/example/mod/ExampleMod.class",
                "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary"
        );
        TestFixtures.assertClassDoesNotReference(
                archive,
                "com/example/mod/ExampleMod.class",
                "net/mezzdev/fixture/library/FixtureLibrary"
        );
    }

    @Test
    void failsWhenRelocationWouldCreateDuplicateJarEntry() throws IOException {
        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        Jar archiveTask = createArchiveTask(project);
        Path archive = archiveTask.getArchiveFile().get().getAsFile().toPath();

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("net/mezzdev/fixture/library/duplicate.txt", "first");
        entries.put("com/example/modshade/net/mezzdev/fixture/library/duplicate.txt", "second");
        TestFixtures.createJar(archive, tempDir, List.of(), entries);

        RelocateModShadeJarAction action = relocationAction(
                project,
                List.of(),
                List.of(new RelocationRule(
                        "net.mezzdev.fixture",
                        "com.example.modshade.net.mezzdev.fixture"
                ))
        );

        assertThrows(GradleException.class, () -> action.execute(archiveTask));
    }

    private Path compileRelocationFixtureClasses() throws IOException {
        Path sourceDir = tempDir.resolve("sources");
        Path classesDir = tempDir.resolve("classes");
        Path library = sourceDir.resolve("net/mezzdev/fixture/library/FixtureLibrary.java");
        Path service = sourceDir.resolve("net/mezzdev/fixture/library/FixtureService.java");
        Path provider = sourceDir.resolve("net/mezzdev/fixture/library/impl/Provider.java");
        Path versioned = sourceDir.resolve("net/mezzdev/fixture/library/Versioned.java");
        Path mod = sourceDir.resolve("com/example/mod/ExampleMod.java");

        TestFixtures.writeJavaSource(
                library,
                """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                service,
                """
                        package net.mezzdev.fixture.library;

                        public interface FixtureService {
                            String value();
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                provider,
                """
                        package net.mezzdev.fixture.library.impl;

                        import net.mezzdev.fixture.library.FixtureService;

                        public final class Provider implements FixtureService {
                            @Override
                            public String value() {
                                return "provider";
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                versioned,
                """
                        package net.mezzdev.fixture.library;

                        public final class Versioned {
                            public String value() {
                                return FixtureLibrary.value();
                            }
                        }
                        """
        );
        TestFixtures.writeJavaSource(
                mod,
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
        TestFixtures.compileJavaSources(classesDir, List.of(library, service, provider, versioned, mod), List.of());
        return classesDir;
    }

    private Jar createArchiveTask(Project project) {
        Jar archiveTask = project.getTasks().register("testArchive", Jar.class).get();
        archiveTask.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
        archiveTask.getArchiveFileName().set("test.jar");
        return archiveTask;
    }

    private RelocateModShadeJarAction relocationAction(
            Project project,
            List<Path> dependencyJars,
            List<RelocationRule> explicitRules
    ) {
        FileCollection dependencyFiles = project.files(dependencyJars.stream()
                .map(Path::toFile)
                .toList());
        Property<String> relocationBase = project.getObjects().property(String.class);
        relocationBase.set("com.example.modshade");
        return new RelocateModShadeJarAction(dependencyFiles, relocationBase, explicitRules);
    }
}
