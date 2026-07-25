package net.mezzdev.modshade.relocation;

import net.mezzdev.modshade.TestFixtures;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelocateSourceFilesActionTest {
    @TempDir
    Path tempDir;

    @Test
    void relocatesSourcePathsPackagesAndImports() throws IOException {
        Path dependencyJar = tempDir.resolve("dependency.jar");
        TestFixtures.createJar(dependencyJar, List.of("net/mezzdev/fixture/library/FixtureLibrary.class"));
        Path sourceRoot = tempDir.resolve("sources");
        Path outputRoot = tempDir.resolve("relocated-sources");
        TestFixtures.writeJavaSource(
                sourceRoot.resolve("net/mezzdev/fixture/library/FixtureLibrary.java"),
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
                sourceRoot.resolve("com/example/mod/ExampleMod.java"),
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

        Project project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
        project.copy(copy -> {
            copy.from(sourceRoot);
            copy.into(outputRoot);
            copy.eachFile(sourceAction(project, dependencyJar));
            copy.setIncludeEmptyDirs(false);
        });

        Path relocatedLibrarySource = outputRoot.resolve("com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.java");
        Path modSource = outputRoot.resolve("com/example/mod/ExampleMod.java");
        assertTrue(Files.isRegularFile(relocatedLibrarySource));
        assertTrue(Files.isRegularFile(modSource));
        assertFalse(Files.exists(outputRoot.resolve("net/mezzdev/fixture/library/FixtureLibrary.java")));

        String relocatedLibraryText = Files.readString(relocatedLibrarySource, StandardCharsets.UTF_8);
        assertTrue(relocatedLibraryText.contains("package com.example.modshade.net.mezzdev.fixture.library;"));

        String modSourceText = Files.readString(modSource, StandardCharsets.UTF_8);
        assertTrue(modSourceText.contains("import com.example.modshade.net.mezzdev.fixture.library.FixtureLibrary;"));
        assertFalse(modSourceText.contains("import net.mezzdev.fixture.library.FixtureLibrary;"));
    }

    private RelocateSourceFilesAction sourceAction(Project project, Path dependencyJar) {
        FileCollection dependencyFiles = project.files(dependencyJar.toFile());
        Property<String> relocationBase = project.getObjects().property(String.class);
        relocationBase.set("com.example.modshade");
        return new RelocateSourceFilesAction(dependencyFiles, relocationBase, List.of());
    }
}
