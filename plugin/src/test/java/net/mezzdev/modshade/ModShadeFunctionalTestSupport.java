package net.mezzdev.modshade;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class ModShadeFunctionalTestSupport {
    protected static final String MOD_CLASS = "com/example/mod/ExampleMod.class";
    protected static final String ORIGINAL_LIBRARY_CLASS = "net/mezzdev/fixture/library/FixtureLibrary.class";
    protected static final String ORIGINAL_LIBRARY_INTERNAL_NAME = "net/mezzdev/fixture/library/FixtureLibrary";
    protected static final String DEFAULT_RELOCATED_LIBRARY_CLASS = "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary.class";
    protected static final String DEFAULT_RELOCATED_LIBRARY_INTERNAL_NAME = "com/example/modshade/net/mezzdev/fixture/library/FixtureLibrary";
    protected static final String DEFAULT_RELOCATED_HELPER_CLASS = "com/example/modshade/net/mezzdev/fixture/helper/FixtureHelper.class";
    protected static final String DEFAULT_RELOCATED_TRANSITIVE_CLASS = "com/example/modshade/net/mezzdev/fixture/transitive/FixtureTransitive.class";
    protected static final String DEFAULT_RELOCATED_EXTERNAL_TRANSITIVE_CLASS = "com/example/modshade/net/mezzdev/fixture/external/FixtureExternalTransitive.class";

    @TempDir
    protected Path tempDir;

    protected void writeBasicProject(Path repo) throws IOException {
        writeBasicProject(repo, """
                tasks.register<net.mezzdev.modshade.task.ModShadeJar>("modShadeJar") {
                    fromJar()
                }
                """);
    }

    protected void writeBasicProject(Path repo, String modShadeBlock) throws IOException {
        writeBasicProjectWithImports(repo, "", modShadeBlock);
    }

    protected void writeBasicProjectWithImports(Path repo, String imports, String buildScriptBody) throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle.kts"), "rootProject.name = \"simple-mod\"\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle.kts"), """
                %s

                plugins {
                    java
                    `maven-publish`
                    id("net.mezzdev.modshade")
                }

                group = "com.example"
                version = "1.0"

                repositories {
                    maven {
                        url = uri("%s")
                    }
                }

                dependencies {
                    modShadeImplementation("net.mezzdev.fixture:library:1.0")
                }

                %s
                """.formatted(imports, repo.toUri(), buildScriptBody), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
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
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );
    }

    protected void writeGroovyBasicProject(Path repo, String buildScriptBody) throws IOException {
        Files.writeString(tempDir.resolve("settings.gradle"), "rootProject.name = 'simple-mod'\n", StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'net.mezzdev.modshade'
                }

                group = 'com.example'
                version = '1.0'

                repositories {
                    maven {
                        url = uri('%s')
                    }
                }

                dependencies {
                    modShadeImplementation 'net.mezzdev.fixture:library:1.0'
                }

                %s
                """.formatted(repo.toUri(), buildScriptBody), StandardCharsets.UTF_8);
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
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
        Files.createDirectories(tempDir.resolve("src/main/resources"));
        Files.writeString(
                tempDir.resolve("src/main/resources/fabric.mod.json"),
                "{\"schemaVersion\":1,\"id\":\"simple_mod\",\"version\":\"1.0.0\"}",
                StandardCharsets.UTF_8
        );
    }

    protected void writeMultiProjectWithLocalPlainHelperSources() throws IOException {
        writeMultiProjectWithLocalPlainHelperSources("""
                modShade {
                    shadeSourcesJar()
                }
                """);
    }

    protected void writeMultiProjectWithLocalPlainHelperSources(String buildScriptBody) throws IOException {
        Files.writeString(
                tempDir.resolve("settings.gradle.kts"),
                """
                        rootProject.name = "simple-mod"
                        include("plain-helper")
                        """,
                StandardCharsets.UTF_8
        );
        Files.writeString(
                tempDir.resolve("build.gradle.kts"),
                """
                        plugins {
                            java
                            id("net.mezzdev.modshade")
                        }

                        group = "com.example"
                        version = "1.0"

                        repositories {
                            mavenCentral()
                        }

                        java {
                            withSourcesJar()
                        }

                        dependencies {
                            modShadeImplementation(project(":plain-helper"))
                        }

                        %s
                        """.formatted(buildScriptBody),
                StandardCharsets.UTF_8
        );
        Files.createDirectories(tempDir.resolve("plain-helper"));
        Files.writeString(
                tempDir.resolve("plain-helper/build.gradle.kts"),
                """
                        plugins {
                            java
                        }
                        """,
                StandardCharsets.UTF_8
        );
        TestFixtures.writeJavaSource(
                tempDir.resolve("src/main/java/com/example/mod/ExampleMod.java"),
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
        TestFixtures.writeJavaSource(
                tempDir.resolve("plain-helper/src/main/java/net/mezzdev/fixture/library/FixtureLibrary.java"),
                """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """
        );
    }

    protected Path publishHelperLibrary(Path repo) throws IOException {
        return publishHelperLibrary(repo, List.of());
    }

    protected Path publishHelperLibrary(Path repo, List<MavenDependency> dependencies) throws IOException {
        return publishFixtureLibrary(
                repo,
                "helper",
                """
                        package net.mezzdev.fixture.helper;

                        public final class FixtureHelper {
                            public static String value() {
                                return "helper";
                            }
                        }
                        """,
                dependencies,
                List.of()
        );
    }

    protected Path publishExternalTransitiveLibrary(Path repo) throws IOException {
        return publishFixtureLibrary(
                repo,
                "external-transitive",
                """
                        package net.mezzdev.fixture.external;

                        public final class FixtureExternalTransitive {
                            public static String value() {
                                return "external";
                            }
                        }
                        """,
                List.of(),
                List.of()
        );
    }

    protected Path publishLibraryWithOptionalHelperUse(Path repo, boolean useHelper) throws IOException {
        String source = useHelper
                ? """
                        package net.mezzdev.fixture.library;

                        import net.mezzdev.fixture.helper.FixtureHelper;

                        public final class FixtureLibrary {
                            public static String value() {
                                return FixtureHelper.value();
                            }
                        }
                        """
                : """
                        package net.mezzdev.fixture.library;

                        public final class FixtureLibrary {
                            public static String value() {
                                return "fixture";
                            }
                        }
                        """;
        Path helperJar = repo.resolve("net/mezzdev/fixture/helper/1.0/helper-1.0.jar");
        List<Path> classpath = useHelper ? List.of(helperJar) : List.of();
        return publishFixtureLibrary(
                repo,
                "library",
                source,
                List.of(new MavenDependency("net.mezzdev.fixture", "helper", "1.0")),
                classpath
        );
    }

    protected Path publishFixtureLibrary(
            Path repo,
            String artifact,
            String sourceText,
            List<MavenDependency> dependencies,
            List<Path> classpath
    ) throws IOException {
        return publishFixtureLibrary(repo, artifact, sourceText, dependencies, classpath, Map.of());
    }

    protected Path publishFixtureLibrary(
            Path repo,
            String artifact,
            String sourceText,
            List<MavenDependency> dependencies,
            List<Path> classpath,
            Map<String, String> extraTextEntries
    ) throws IOException {
        Path workDir = Files.createTempDirectory(tempDir, "fixture-" + artifact);
        String sourceFileName = switch (artifact) {
            case "helper" -> "FixtureHelper.java";
            case "external-transitive" -> "FixtureExternalTransitive.java";
            default -> "FixtureLibrary.java";
        };
        Path source = workDir.resolve("src").resolve(sourceFileName);
        TestFixtures.writeJavaSource(source, sourceText);

        Path classesDir = workDir.resolve("classes");
        TestFixtures.compileJavaSources(classesDir, List.of(source), classpath);

        String group = "net.mezzdev.fixture";
        String version = "1.0";
        Path moduleDir = repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version);
        Files.createDirectories(moduleDir);

        List<String> classEntries;
        try (var paths = Files.walk(classesDir)) {
            classEntries = paths
                    .filter(Files::isRegularFile)
                    .map(classesDir::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .sorted()
                    .toList();
        }

        Map<String, String> textEntries = new LinkedHashMap<>();
        textEntries.put("META-INF/maven/" + group + "/" + artifact + "/pom.properties", "version=" + version);
        textEntries.putAll(extraTextEntries);
        Path jar = moduleDir.resolve(artifact + "-" + version + ".jar");
        TestFixtures.createJar(jar, classesDir, classEntries, textEntries);

        Files.writeString(
                moduleDir.resolve(artifact + "-" + version + ".pom"),
                """
                        <project xmlns="http://maven.apache.org/POM/4.0.0"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>%s</groupId>
                            <artifactId>%s</artifactId>
                            <version>%s</version>
                            %s
                        </project>
                        """.formatted(group, artifact, version, formatDependencies(dependencies)),
                StandardCharsets.UTF_8
        );
        return jar;
    }

    protected static String formatDependencies(List<MavenDependency> dependencies) {
        if (dependencies.isEmpty()) {
            return "";
        }

        StringBuilder dependencyText = new StringBuilder();
        dependencyText.append("<dependencies>\n");
        for (MavenDependency dependency : dependencies) {
            dependencyText.append("""
                                    <dependency>
                                        <groupId>%s</groupId>
                                        <artifactId>%s</artifactId>
                                        <version>%s</version>
                                    </dependency>
                    """.formatted(dependency.group(), dependency.artifact(), dependency.version()));
        }
        dependencyText.append("                            </dependencies>");
        return dependencyText.toString();
    }

    protected record MavenDependency(String group, String artifact, String version) {
    }

    protected Path defaultShadedJar() {
        return tempDir.resolve("build/libs/simple-mod-1.0-modshade.jar");
    }

    protected void writeConfigurationCacheProperty() throws IOException {
        Files.writeString(tempDir.resolve("gradle.properties"), "org.gradle.configuration-cache=true\n", StandardCharsets.UTF_8);
    }

    protected GradleRunner gradleWithConfigurationCache(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--configuration-cache");
        arguments.add("--configuration-cache-problems=fail");
        return gradle(arguments.toArray(String[]::new));
    }

    protected GradleRunner gradle812WithConfigurationCache(String... tasks) {
        List<String> arguments = new ArrayList<>(List.of(tasks));
        arguments.add("--configuration-cache");
        arguments.add("--configuration-cache-problems=fail");
        return gradle(arguments.toArray(String[]::new))
                .withGradleVersion("8.12.1");
    }

    protected static void assertTaskCompleted(BuildResult result, String taskPath) {
        TaskOutcome outcome = Objects.requireNonNull(result.task(taskPath)).getOutcome();
        assertTrue(
                outcome == TaskOutcome.SUCCESS || outcome == TaskOutcome.UP_TO_DATE || outcome == TaskOutcome.FROM_CACHE,
                () -> taskPath + " should complete, but was " + outcome
        );
    }

    protected static void assertConfigurationCacheStored(BuildResult result) {
        assertTrue(
                result.getOutput().contains("Configuration cache entry stored."),
                "expected Gradle to store a configuration cache entry"
        );
    }

    protected static void assertConfigurationCacheReused(BuildResult result) {
        assertTrue(
                result.getOutput().contains("Reusing configuration cache."),
                "expected Gradle to reuse the configuration cache entry"
        );
    }

    protected GradleRunner gradle(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(tempDir.toFile())
                .withPluginClasspath()
                .withArguments(arguments)
                .forwardOutput();
    }

}
